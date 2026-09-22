/*
 * Copyright (C) 2022 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import jwt_decode from 'jwt-decode';
import {mapCalendarListEntry, mapGoogleEvent, mergeEventLists} from './googleCalendarMapping.js';
import {defineSdkHandle} from '../js/agendaConnectorUtils.js';

/**
 * The calendar eXo copies of meetings are pushed to. Deliberately not derived
 * from the calendar listing: reading spans all of the user's calendars, but
 * the write destination stays the account's primary calendar, where every
 * copy pushed so far already lives — pointing new copies elsewhere would
 * strand the existing ones. A future decision to change the destination
 * belongs here, and only here.
 */
const PUSH_CALENDAR_ID = 'primary';

/**
 * How long the account's calendar list is reused: long enough for the reads
 * of one page to share an answer, short enough that a newly subscribed
 * calendar appears without a reload.
 */
const CALENDAR_LIST_TTL_MS = 300000;

/**
 * Page size for a whole-period read. Google's documented ceiling; the answer
 * is complete because the token is followed, not because this is large.
 */
const EVENTS_PAGE_SIZE = 2500;

/**
 * Page bound for a count-bounded read. A windowed read ends at its timeMax;
 * a count-bounded one has no window, and Google documents that a page "may
 * be less than this value, or none at all, even if there are more events
 * matching the query" — so empty-but-tokened pages would be followed without
 * limit.
 * <p>
 * Also the count past which a windowed read, which is never truncated, says
 * once that it is running long. Two uses, one bound: raising this moves the
 * warning with the cap.
 */
const MAX_EVENT_PAGES = 10;

/**
 * The calendar list read for an account: the in-flight or settled promise,
 * the account, and when it started. The promise rather than the entries, so
 * callers that arrive while a read is running share it instead of each
 * issuing their own — on an agenda page a remote read and the calendars
 * panel routinely start in the same tick.
 * <p>
 * `at` is when the read <em>started</em>, not when it answered: an entry has
 * to be visible before it settles or there is nothing for a concurrent caller
 * to share, so the TTL runs from the start of the read and a slow one spends
 * part of its own TTL.
 */
let calendarListCache = null;

/**
 * Marks a rejection as "this account must be connected again". Agenda reads
 * it off `credentialsErrorCode` and tells the user to reconnect rather than
 * reporting a copy that failed (AgendaConnector.copyFailureMessageKey).
 */
const CREDENTIALS_ERROR_CODE = 'google_credentials_refused';

/**
 * The one Identity Services error type meaning the user backed out. Its
 * siblings — popup_failed_to_open, unknown, and the undocumented
 * missing_required_parameter the bundle also exports — are real failures.
 */
const CONSENT_DISMISSED_TYPE = 'popup_closed';

/**
 * What agenda tests to stay quiet about a dismissed consent. A gapi.auth2
 * code, kept because agenda's test is written on it; see consentFailure().
 */
const CONSENT_DISMISSED_LEGACY_CODE = 'popup_closed_by_user';

/**
 * A rejection agenda will read as "reconnect this account".
 *
 * @param {String} message what went wrong, for whoever reads the rejection
 * @param {Object} cause the failure this one replaces, when there was one
 * @returns {Error} the error to reject with
 */
function credentialsError(message, cause) {
  const error = new Error(message);
  error.code = CREDENTIALS_ERROR_CODE;
  if (cause) {
    error.cause = cause;
  }
  return error;
}

export default {
  name: 'agenda.googleCalendar',
  description: 'agenda.googleCalendar.description',
  avatar: '/agenda-connectors/skin/images/Google.png',
  isOauth: true,
  mandatorySecretKey: true,
  CLIENT_ID: null,
  DISCOVERY_DOCS: ['https://www.googleapis.com/discovery/v1/apis/calendar/v3/rest'],
  SCOPE_WRITE: 'https://www.googleapis.com/auth/calendar.events',
  canConnect: true,
  canPush: false,
  canListCalendars: true,
  credentialsErrorCode: CREDENTIALS_ERROR_CODE,
  initialized: false,
  isSignedIn: false,
  pushing: false,
  rank: 10,
  init(connectionStatusChangedCallback, loadingCallback, apiKey) {
    if (!apiKey) {
      throw new Error('Google connector can\'t be enabled with empty Client API Key.');
    }
    this.CLIENT_ID = apiKey;
    // Already initialized
    if (this.initialized) {
      return;
    }
    this.initialized = true;
    this.connectionStatusChangedCallback = connectionStatusChangedCallback;
    this.loadingCallback = loadingCallback;

    initGoogleConnector(this);
  },
  /**
   * A usable token for the connected account.
   * <p>
   * <b>interactive</b> — only connect() passes it — may open the consent
   * popup. <b>background</b>, the default, must never open one over a page
   * the user was merely visiting: it rejects instead, and the caller reports
   * an account it could not read.
   * <p>
   * Every path settles. Four did not, and the shape recurs: a promise settled
   * from an SDK callback needs every branch to reach resolve or reject, the
   * callbacks installed elsewhere included (EXO-90496).
   *
   * @param {Boolean} refresh whether to spend the stored refresh token
   *        instead of reading the access token already held
   * @param {Boolean} interactive whether the user may be prompted
   * @returns {Promise} resolves with a token response carrying an
   *          access_token, rejects when none could be obtained
   */
  authorize(refresh, interactive) {
    return new Promise((resolve, reject) => {
      // This call's identity in the connector's single pending slot. Several
      // authorize() calls overlap routinely, so settling clears the slot only
      // when it is still ours: clearing unconditionally let a background call
      // disarm the popup's rejector, leaving connect() pending.
      const slot = {reject};
      const settle = (settler, value) => {
        if (this.pendingAuthorization === slot) {
          this.pendingAuthorization = null;
        }
        settler(value);
      };
      const accept = tokenResponse => {
        if (!tokenResponse?.access_token) {
          settle(reject, credentialsError('Google answered no access token'));
          return;
        }
        this.gapi.client.setToken(tokenResponse);
        if (this.cientOauth) {
          this.canPush = this.cientOauth.hasGrantedAllScopes(tokenResponse, this.SCOPE_WRITE);
        }
        settle(resolve, tokenResponse);
      };
      const refuse = error => settle(reject, error);
      /**
       * Opens the consent popup for this call, and only this call.
       * <p>
       * The code client holds ONE callback, read at response time rather than
       * captured by requestCode(), so whoever assigned it last receives the
       * consent. Assigning it on every authorize() — as this did — meant a
       * background read renewing a token while the popup was open took
       * delivery of the user's consent: the token was installed, but on a
       * promise that had already settled, so connect() never settled, the
       * account was never recorded, and nothing reported it. A granted consent
       * fires no error_callback, so there was no second way out.
       * <p>
       * A second popup is refused rather than opened. The SDK re-randomises
       * the request id per requestCode() and overwrites the window handle, so
       * a second one silently strands the first whatever we do with the
       * callback — it would receive neither the consent nor a popup_closed.
       *
       * @returns {void}
       */
      const askConsent = () => {
        if (this.pendingAuthorization) {
          refuse(new Error('A Google consent popup is already open'));
          return;
        }
        this.pendingAuthorization = slot;
        this.codeClient.callback = (response) => {
          if (!response?.code) {
            settle(reject, new Error('Google consent was not granted'));
            return;
          }
          return requestToken(response.code, response.scope, window.location.origin)
            .then(accept)
            .catch(refuse);
        };
        this.codeClient.requestCode();
      };
      try {
        if (refresh) {
          // The last token obtainable without the user. The rejection is
          // replaced, not dropped: agenda needs the reconnect code, and the
          // endpoint's own failure travels on as the cause. An expired token
          // is a normal flow, so it is not logged.
          return refreshToken().then(accept)
            .catch(error => refuse(credentialsError('Google refused to refresh the access token', error)));
        } else if (this.user) {
          return getStoredToken().then(accept).catch(error => {
            // Only a fresh consent can produce one, and only the user gives it.
            if (error.status === 404) {
              if (interactive) {
                askConsent();
              } else {
                refuse(credentialsError('No Google token stored for this account', error));
              }
            } else {
              refuse(error);
            }
          });
        } else if (interactive) {
          askConsent();
        } else {
          refuse(new Error('No connected Google account to authorize'));
        }
      } catch (err) {
        refuse(err);
      }
    });
  },
  authenticate() {
    return new Promise((resolve, reject) => {
      deleteCookie('g_state');
      try {
        this.identity.prompt(notification => {
          if (notification.getDismissedReason() === 'credential_returned') {
            resolve();
          } else
          if (notification.getDismissedReason() === 'user_cancel') {
            this.loadingCallback(this, false);
            this.connectionStatusChangedCallback(this, false, 'user_cancel');
            resolve();
          }
        });
      } catch (err) {
        reject(err);
      }
    });
  },
  connect(askWriteAccess) {
    this.loadingCallback(this, true);
    if (askWriteAccess && !this.canPush) {
      // The one call allowed to open the consent popup.
      return this.authorize(false, true).then(() => {
        return this.authenticate().then(() => {
          return new Promise((resolve, reject) => {
            if (this.credential) {
              const userEmail = this.credential.email;
              resolve(userEmail);
            } else {
              // Never a bare reject(): agenda's catch reads error.error.
              reject(new Error('Google signed in without returning an account'));
            }
          });
        });
      });
    } else {
      return this.authenticate().then(() => {
        return new Promise((resolve, reject) => {
          if (this.credential) {
            resolve(this.credential.email);
          } else {
            reject(new Error('Google signed in without returning an account'));
          }
        });
      });
    }
  },
  /**
   * Forgets that write access was granted.
   * <p>
   * canPush is not a property of this connector but of the account attached to
   * it: it records that the user granted the write scope, and it is recomputed
   * from hasGrantedAllScopes() every time a token is obtained. Disconnecting
   * revokes that grant, so keeping the flag would claim a permission that no
   * longer exists — connect() reads it to decide whether to ask for write
   * access, and would skip asking, leaving the client without a usable token
   * until the first copy failed.
   *
   * @returns {void}
   */
  resetPushAbility() {
    this.canPush = false;
  },
  disconnect() {
    this.loadingCallback(this, true);
    // Keeping them would serve this account's calendars to the next one.
    forgetCalendarList();
    return removeToken().then(() => {
      if (this.gapi.client.getToken() && this.cientOauth || this.user) {
        this.cientOauth.revoke(this.gapi.client.getToken());
        this.gapi.client.setToken('');
        if (this.user) {
          this.identity.revoke(this.user);
          this.identity.disableAutoSelect();
        }
      }
    });

  },
  /**
   * The account's events over a period.
   *
   * @param {String} periodStartDate an RFC3339 timestamp with time-zone
   *        offset, as $agendaUtils.toRFC3339(date, false, true) produces
   * @param {String} periodEndDate the same, for the end of the period
   * @returns {Promise} resolves with the events, rejects when the account
   *          could not be read
   */
  getEvents(periodStartDate, periodEndDate) {
    return readEvents(this, {timeMin: periodStartDate, timeMax: periodEndDate});
  },
  /**
   * The account's next <code>count</code> events from a date, with no end
   * date at all.
   * <p>
   * The optional half of the connector contract a view uses when it shows "the
   * next few meetings" rather than a period: it has no period to give, and
   * inventing one is what made it expensive. Reading every calendar of the
   * account means a horizon is paid once per calendar, so a year of it — the
   * only window the timeline widget ever asked for — expanded a year of every
   * recurrence of every calendar to fill a list of ten items (EXO-90496).
   * Bounding by the count asks Google for what the view wants: a bounded
   * number of short pages per calendar, where a horizon cost a year of
   * expanded recurrences. Breadth is unchanged — one read per calendar, run
   * concurrently — only the depth of each stops depending on a guess.
   * <p>
   * Four things it does not promise: the bound is on events, while a view
   * truncates by its own rule — agenda's timeline at `limit` entries, a
   * multi-day event taking one per day — so this gives enough, not an exact
   * answer; equal start times have no tie-break; the count is a floor per
   * calendar, not a ceiling, since a short page followed by a full one
   * overshoots; and a calendar that cannot produce the count within
   * MAX_EVENT_PAGES is answered short, with an error logged.
   * <p>
   * The count is required and positive: with no end date it is the only
   * bound, and this is an SPI other addons call, so it is refused here rather
   * than left to a caller's arithmetic.
   *
   * @param {String} periodStartDate an RFC3339 timestamp with time-zone
   *        offset, as $agendaUtils.toRFC3339(date, false, true) produces
   * @param {Number} count how many events the caller can show, at least 1
   * @returns {Promise} resolves with enough events to fill a list of count
   *          per calendar, merged; rejects on a missing or non-positive
   *          count, or when the account could not be read
   */
  getUpcomingEvents(periodStartDate, count) {
    if (!(count > 0)) {
      return Promise.reject(new Error('getUpcomingEvents needs a positive count: it is the only bound on a read that has no end date'));
    }
    return readEvents(this, {timeMin: periodStartDate, wanted: count});
  },
  /**
   * The calendars of the connected Google account, in the shape agenda
   * expects from any connector able to list them — the contract the CalDAV
   * connector established: one entry per calendar, each with an identity, a
   * name, a colour that is always usable, and whether it may be written to.
   *
   * The identity is Google's calendar id, never the display name: renaming a
   * calendar must not detach whatever agenda associated with it, and it is
   * the same id the fetched events are tagged with, which is what makes the
   * left panel's per-calendar checkboxes actually filter the grid.
   *
   * An expired token is renewed once, the same way getEvents does it; any
   * other failure is the caller's to handle — agenda logs and drops the one
   * connector rather than emptying its whole section.
   *
   * @returns {Promise} resolves with one {id, name, color, readOnly} per
   *          calendar, or an empty list when the API is not ready
   */
  listCalendars() {
    if (!this.gapi || !this.gapi.client || !this.gapi.client.calendar) {
      return Promise.resolve([]);
    }
    return retrieveCalendarList(this)
      .catch(error => {
        if (!isAuthenticationFailure(error)) {
          throw error;
        }
        return this.authorize().then(() => retrieveCalendarList(this, true));
      })
      .then(entries => entries.map(mapCalendarListEntry));
  },
  deleteEvent(event, connectorRecurringEventId) {
    return this.saveEvent(event, connectorRecurringEventId, true);
  },
  pushEvent(event, connectorRecurringEventId) {
    return this.saveEvent(event, connectorRecurringEventId, false);
  },
  /**
   * Writes one meeting to the account, or removes it. Same three attempts and
   * the same flat shape as the reads: the nested form left this pending, and
   * a push that never settles leaves the pushing flag raised (EXO-90496).
   *
   * @param {Object} event the agenda event to write
   * @param {String} connectorRecurringEventId parent recurrent event id
   * @param {Boolean} deleteEvent whether to delete rather than save
   * @returns {Promise} resolves with the written Google event
   */
  saveEvent(event, connectorRecurringEventId, deleteEvent) {
    if (!this.gapi) {
      return Promise.reject(new Error('Not connected'));
    }
    this.pushing = true;
    const push = () => pushEventToGoogle(this, event, connectorRecurringEventId, deleteEvent);
    return push()
      .catch(error => {
        if (!isAuthenticationFailure(error)) {
          throw error;
        }
        return this.authorize().then(push);
      })
      .catch(error => {
        if (!isAuthenticationFailure(error)) {
          throw error;
        }
        return this.authorize(true).then(push);
      })
      .finally(() => this.pushing = false);
  },
};

/**
 * Reads the account's events, renewing the token if that is what was refused.
 * <p>
 * Three attempts: the token in hand, the stored one, then a refreshed one —
 * an expired access token is the ordinary case an hour after the last page.
 * Flat rather than nested so that every branch visibly ends in a returned
 * promise or a throw; the nested form left it pending (EXO-90496).
 *
 * @param {Object} connector Google Connector SPI
 * @param {Object} request what to read — {timeMin, timeMax, wanted}
 * @returns {Promise} resolves with the mapped events, rejects when the
 *          account could not be read
 */
function readEvents(connector, request) {
  if (!connector.gapi?.client?.calendar) {
    return Promise.resolve(null);
  }
  connector.loadingCallback(connector, true);
  const read = () => retrieveEvents(connector, request);
  return read()
    .catch(error => {
      if (!isAuthenticationFailure(error)) {
        throw error;
      }
      return connector.authorize().then(read);
    })
    .catch(error => {
      if (!isAuthenticationFailure(error)) {
        throw error;
      }
      return connector.authorize(true).then(read);
    })
    .finally(() => connector.loadingCallback(connector, false));
}

/**
 * Rejects the authorization waiting on the consent popup, if one is. The SDK
 * reports popup failures through a callback installed once at init, which
 * knows nothing of any one call's promise; the slot connects the two. Only
 * one popup can be open at a time, so one slot is enough.
 *
 * @param {Object} connector Google Connector SPI
 * @param {Object} error what the SDK reported
 * @returns {void}
 */
function failPendingAuthorization(connector, error) {
  const pending = connector.pendingAuthorization;
  if (pending) {
    connector.pendingAuthorization = null;
    pending.reject(consentFailure(error));
  }
}

/**
 * The consent popup's failure, in the shape agenda reads.
 * <p>
 * Identity Services errors carry message/stack/type and no <code>error</code>
 * field, while agenda tests <code>error.error</code> against
 * 'popup_closed_by_user' — gapi.auth2 vocabulary this connector replaced. So
 * that test has matched nothing since the move; unnoticed while the popup's
 * failure never settled, and every dismissal would now read as a failure.
 * <p>
 * Translated here, and only popup_closed is marked: popup_failed_to_open,
 * unknown and the undocumented missing_required_parameter are real failures.
 * Widening agenda's test would have silenced them, and agenda has no business
 * knowing one provider's error words.
 *
 * @param {Object} error what Identity Services reported
 * @returns {Error} the rejection, marked as a dismissal only when it is one
 */
function consentFailure(error) {
  const type = error?.type || 'unknown';
  const failure = new Error(error?.message || `Google consent failed: ${type}`);
  failure.type = type;
  failure.cause = error;
  if (type === CONSENT_DISMISSED_TYPE) {
    failure.error = CONSENT_DISMISSED_LEGACY_CODE;
  }
  return failure;
}

/**
 * Whether a failure is Google refusing the token rather than refusing the
 * request: the only kind a new token can fix, and so the only one worth
 * spending an authorization round on.
 *
 * @param {Object} error the rejection to classify
 * @returns {Boolean} true when the token is what was refused
 */
function isAuthenticationFailure(error) {
  return !!error && (error.status === 401 || error.status === 403);
}

/**
 * The raw calendarList entries of the connected account, every page of them:
 * the API answers by pages of at most 250, so stopping at the first page
 * would silently drop the calendars of a user who has many.
 *
 * Hidden calendars are left out by the API's own default — a calendar the
 * user hid in Google's UI is one they do not want painted here either.
 *
 * @param {Object}
 *          connector Google Connector SPI
 * @param {String}
 *          pageToken token of the page to fetch, none for the first
 * @param {Array}
 *          accumulated entries of the pages already fetched
 * @returns {Promise} a promise with the account's calendarList entries
 */
function fetchCalendarListPage(connector, pageToken, accumulated) {
  const options = {};
  if (pageToken) {
    options.pageToken = pageToken;
  }
  return connector.gapi.client.calendar.calendarList.list(options)
    .then(response => {
      const result = response.result || {};
      const entries = (accumulated || []).concat(result.items || []);
      return result.nextPageToken
        ? fetchCalendarListPage(connector, result.nextPageToken, entries)
        : entries;
    });
}

/**
 * The account's calendar list, read at most once per TTL, concurrent callers
 * included. Every event read needs it now, on top of listCalendars() asking
 * for it separately.
 * <p>
 * Module scope, not the connector: the connector lives in agenda's reactive
 * data and nothing here needs Vue to watch it (as for the SDK handles of
 * EXO-90245). Keyed by account, because disconnect() alone is not enough —
 * agenda only calls it when the browser session is signed in, so one
 * account's calendar ids could be asked for on another's token, every call
 * 404ing into the per-calendar catch and the grid showing nothing.
 *
 * @param {Object} connector Google Connector SPI
 * @param {Boolean} force whether to ignore whatever is cached, used after a
 *        token was renewed
 * @returns {Promise} a promise with the account's calendarList entries
 */
function retrieveCalendarList(connector, force) {
  // AgendaConnector sets user to '' when there is no account or it carries no
  // remoteUserId, and two such connectors would compare equal. Nothing is
  // served or stored without an id.
  const identified = !!connector.user;
  const usable = identified
    && calendarListCache
    && calendarListCache.user === connector.user
    && (Date.now() - calendarListCache.at) < CALENDAR_LIST_TTL_MS;
  if (!force && usable) {
    return calendarListCache.promise;
  }
  const promise = fetchCalendarListPage(connector);
  if (identified) {
    // Stored before it settles, so a caller arriving mid-read waits on this
    // one. Cleared on failure, or every later caller would inherit it.
    const entry = {at: Date.now(), promise, user: connector.user};
    calendarListCache = entry;
    promise.catch(() => {
      if (calendarListCache === entry) {
        calendarListCache = null;
      }
    });
  }
  return promise;
}

/**
 * Forgets the cached calendar list, so the next read asks Google again.
 *
 * @returns {void}
 */
function forgetCalendarList() {
  calendarListCache = null;
}

/**
 * One calendar's events for a request, paged until it is satisfied.
 * <p>
 * <b>maxResults is a page size, never a result count</b>: "The number of
 * events in the resulting page may be less than this value, or none at all,
 * even if there are more events matching the query" (discovery document,
 * revision 20260826). singleEvents expands recurrences, so the server cuts a
 * page short exactly where an open-ended read is likeliest.
 * <p>
 * Hence two bounds, neither droppable: a request with an end date pages to
 * exhaustion, since every event in the period is part of the answer; one with
 * a wanted count pages until it holds that many. Stopping at the first page
 * would drop a busy calendar's tail in the first case and any calendar's head
 * in the second.
 *
 * @param {Object} connector Google Connector SPI
 * @param {Object} calendar the mapped calendar being read
 * @param {Object} request what to read — {timeMin, timeMax, wanted}
 * @param {String} pageToken token of the page to fetch, none for the first
 * @param {Array} accumulated events of the pages already fetched
 * @param {Number} page which page this is, counted from 1, for the cap
 * @returns {Promise} a promise with that calendar's mapped events
 */
function retrieveCalendarEvents(connector, calendar, request, pageToken, accumulated, page = 1) {
  const options = {
    'calendarId': calendar.id,
    'timeMin': request.timeMin,
    'singleEvents': true,
    'orderBy': 'startTime',
    'maxResults': request.wanted || EVENTS_PAGE_SIZE,
  };
  // Left out entirely when the caller bounds by count: "the default is not to
  // filter by start time", which is what makes the read horizonless.
  if (request.timeMax) {
    options.timeMax = request.timeMax;
  }
  if (pageToken) {
    options.pageToken = pageToken;
  }
  return connector.gapi.client.calendar.events.list(options)
    .then(response => {
      const result = response.result || {};
      const events = (accumulated || []).concat((result.items || []).map(event => mapGoogleEvent(event, calendar)));
      const satisfied = request.wanted && events.length >= request.wanted;
      // Only a count-bounded read is capped: truncating a windowed one would
      // drop events inside the period asked for. A long windowed read still
      // says so, or it is invisible but for a slow tab.
      const exhausted = request.wanted && page >= MAX_EVENT_PAGES;
      // Once, on the page that reaches the threshold: a windowed read carries
      // on past it and one line is the signal, not a page each.
      if (result.nextPageToken && !satisfied && page === MAX_EVENT_PAGES) {
        if (exhausted) {
          console.error(`stopped reading Google calendar ${calendar.id} after ${MAX_EVENT_PAGES} pages holding ${events.length} of the ${request.wanted} events asked for`);
        } else {
          // Long, not wrong: this read carries on to exhaustion and its answer
          // is complete, so it is not an incident.
          console.warn(`still reading Google calendar ${calendar.id} after ${page} pages holding ${events.length} events of the requested period`);
        }
      }
      return result.nextPageToken && !satisfied && !exhausted
        ? retrieveCalendarEvents(connector, calendar, request, result.nextPageToken, events, page + 1)
        : events;
    });
}

/**
 * The account's events over the period, gathered from every calendar of the
 * account rather than from the primary one alone. Each event is tagged with
 * the calendar it came from and carries that calendar's real colour — the
 * former single-calendar implementation hardcoded '#FFFFFF', a white event
 * on the white grid.
 *
 * One calendar that fails must not blank the whole agenda: its failure is
 * logged and it contributes no events, while the others still answer. An
 * authentication failure is rethrown instead, because it concerns every
 * calendar and the caller knows how to renew the token.
 *
 * @param {Object}
 *          connector Google Connector SPI
 * @param {Object}
 *          request what to read — {timeMin, timeMax, wanted}
 * @returns {Promise} a promise with list of Google events
 */
function retrieveEvents(connector, request) {
  return retrieveCalendarList(connector)
    .then(entries => entries.map(mapCalendarListEntry))
    .then(calendars => Promise.all(calendars.map(calendar =>
      retrieveCalendarEvents(connector, calendar, request)
        .catch(error => {
          if (isAuthenticationFailure(error)) {
            throw error;
          }
          console.error(`cannot retrieve the events of Google calendar ${calendar.id}`, error);
          return [];
        })
    )))
    // The loading flag stays with readEvents for all three attempts: lowering
    // it from a failed one told the page the read was over mid-retry.
    .then(eventLists => mergeEventLists(eventLists));
}

function deleteCookie(name) {
  document.cookie = `${name}=; Max-Age=0; path=/`;
}

function removeToken() {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/gconnector/token`, {
    credentials: 'include',
    method: 'DELETE'
  }).then((resp) => {
    if (resp && !resp.ok) {
      throw new Error('Error while removing stored token');
    }
  });
}

function refreshToken() {
  const formData = new FormData();
  formData.append('grantType', 'refresh_token');
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/gconnector/refreshaccess`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error while refreshing access token');
    } else {
      return resp.json();
    }
  });
}

function getStoredToken() {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/gconnector/token`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (!resp?.ok) {
      throw resp;
    } else {
      return resp.json();
    }
  });
}
function requestToken(code, scopes, redirect_uri) {
  const formData = new FormData();
  formData.append('code',code);
  formData.append('scopes',scopes);
  formData.append('grantType', 'authorization_code');
  formData.append('redirectUri', redirect_uri);
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/gconnector/oauth2callback`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error while requesting access token');
    } else {
      return resp.json();
    }
  });
}

function checkUserStatus(connector) {
  getStoredToken().then(token => {
    if (connector.user && token?.access_token) {
      connector.isSignedIn = true;
      connector.canPush = connector.cientOauth.hasGrantedAllScopes(token, connector.SCOPE_WRITE);
    }
  // Holding no token is the ordinary state of anyone who has not connected
  // Google, and this runs at init for all of them: without the catch the
  // browser reported an uncaught promise on every page load.
  }).catch(() => {
    connector.isSignedIn = false;
  });
}
/**
 * Load Google Connector API javascript and prepare user authentication and
 * authorization process
 * 
 * @param {Object}
 *          connector Google Connector SPI
 * @returns {void}
 */

function initGoogleConnector(connector) {
  connector.loadingCallback(connector, true);
  window.require(['https://apis.google.com/js/api.js', 'https://accounts.google.com/gsi/client'], () => {
    defineSdkHandle(connector, 'identity', google.accounts.id);
    connector.identity.initialize({
      client_id: connector.CLIENT_ID,
      select_by: 'user',
      use_fedcm_for_prompt: true,
      callback: (credResponse) => {
        if (credResponse && credResponse.credential) {
          const credential = jwt_decode(credResponse.credential);
          connector.isSignedIn = true;
          connector.connectionStatusChangedCallback(connector, {
            user: credential.email,
            id: credential.sub,
          });
          connector.credential = credential;
        } else {
          connector.connectionStatusChangedCallback(connector, false);
        }
      }
    });
    defineSdkHandle(connector, 'gapi', gapi);
    connector.gapi.load('client', function() {
      gapi.client.init({
        discoveryDocs: connector.DISCOVERY_DOCS,
      }).then(function () {
        checkUserStatus(connector);
        defineSdkHandle(connector, 'cientOauth', google.accounts.oauth2);
        defineSdkHandle(connector, 'codeClient', connector.cientOauth.initCodeClient({
          client_id: connector.CLIENT_ID,
          scope: connector.SCOPE_WRITE,
          ux_mode: 'popup',
          error_callback: (error) => {
            // The other way an authorize() started by requestCode() can end;
            // until it rejected here connect() never settled. Translated on
            // the way out — see consentFailure().
            failPendingAuthorization(connector, error);
            connector.loadingCallback(connector, false);
            connector.connectionStatusChangedCallback(connector, false, error);
          }
        }));
      }, function(error) {
        connector.loadingCallback(connector, false);
        connector.connectionStatusChangedCallback(connector, false, error);
      });
    });
    connector.loadingCallback(connector, false);
  }, (error) => {
    connector.canConnect = false;
    connector.loadingCallback(connector, false);
    console.error('Error retrieving Google API Javascript', error);
  });
}

/**
 * Push event into Google account
 * 
 * @param {Object}
 *          connector Google Connector SPI
 * @param {Object}
 *          event Agenda event
 * @param {String}
 *          connectorRecurringEventId Connector parent recurrent event
 *          Identifier
 * @param {Boolean}
 *          deleteEvent whether to delete or save event status
 * @returns {void}
 */
function pushEventToGoogle(connector, event, connectorRecurringEventId, deleteEvent) {
  const connectorEvent = buildConnectorEvent(event, connectorRecurringEventId);
  let retrievingEventPromise = null;
  const isExceptionalOccurrence = connectorRecurringEventId && event.occurrence && event.occurrence.id;
  const isRemoteEvent = event.remoteId && event.remoteProviderName === connector.name;
  const isDeleteEvent = deleteEvent || event.status.toLowerCase() === 'cancelled';

  if (isExceptionalOccurrence || isRemoteEvent || isDeleteEvent) {
    const options = {
      'calendarId': PUSH_CALENDAR_ID,
      'showDeleted': true,
    };
    if (isExceptionalOccurrence) {
      options.eventId = connectorRecurringEventId;
      options.recurringEventId = connectorRecurringEventId;
      options.originalStart = event.occurrence.id;
      retrievingEventPromise = connector.gapi.client.calendar.events.instances(options);
    } else if (isRemoteEvent) {
      options.eventId = event.remoteId;
      retrievingEventPromise = connector.gapi.client.calendar.events.get(options);
    } else {
      retrievingEventPromise = Promise.resolve(null);
    }
  } else {
    retrievingEventPromise = Promise.resolve(null);
  }

  return retrievingEventPromise
    .then(data => {
      const remoteConnectorEventResult = data && data.result;
      let remoteConnectorEvent = null;
      if (remoteConnectorEventResult) {
        if (remoteConnectorEventResult.items) {
          remoteConnectorEvent = remoteConnectorEventResult.items.length && remoteConnectorEventResult.items[0];
        } else if (remoteConnectorEventResult.id) {
          remoteConnectorEvent = remoteConnectorEventResult;
        }
      }
      const pushMethod = isDeleteEvent ?
        connector.gapi.client.calendar.events.delete
        :remoteConnectorEvent ?
          connector.gapi.client.calendar.events.patch:
          connector.gapi.client.calendar.events.insert;

      const options = {
        calendarId: PUSH_CALENDAR_ID,
      };

      if (isDeleteEvent) {
        if (!remoteConnectorEvent || remoteConnectorEvent.status === 'cancelled') {
          return null;
        }
        options.eventId = remoteConnectorEvent.id;
      } else {
        if (remoteConnectorEvent) {
          options.eventId = remoteConnectorEvent.id;
          connectorEvent.id = options.eventId;
          if (isExceptionalOccurrence) {
            connectorEvent.originalStartTime = remoteConnectorEvent.originalStartTime;
            connectorEvent.recurringEventId = remoteConnectorEvent.recurringEventId;
          }
        }
        options.resource = connectorEvent;
      }

      return pushMethod(options);
    })
    .then(resp => resp && resp.result);
}

/**
 * Build event to push into Google
 * 
 * @param {Object}
 *          event Agenda Event object
 * @param {String}
 *          connectorRecurringEventId Connector parent recurrent event
 *          Identifier
 * @returns {void}
 */
function buildConnectorEvent(event, connectorRecurringEventId) {
  const connectorEvent = {};
  if (event.recurrence) {
    connectorEvent.recurrence = [`RRULE:${event.recurrence.rrule}`];
  }
  if (connectorRecurringEventId) {
    connectorEvent.recurringEventId = connectorRecurringEventId;
    if (event.allDay) {
      connectorEvent.originalStartTime = {
        date: event.occurrence.id,
        timeZone: event.timeZoneId
      };
    } else {
      connectorEvent.originalStartTime = {
        dateTime: event.occurrence.id,
        timeZone: event.timeZoneId
      };
    }
  }
  connectorEvent.status = event.status.toLowerCase();

  if (event.allDay) {
    connectorEvent.start = {
      date: event.start,
    };
  } else {
    connectorEvent.start = {
      dateTime: event.start,
      timeZone: event.timeZoneId
    };
  }
  if (event.allDay) {
    const endDate = new Date(event.end);
    endDate.setDate(endDate.getDate() +1);
    const formattedEndDate = `${endDate.getFullYear()  }-${
      pad(endDate.getMonth() + 1)  }-${
      pad(endDate.getDate())}`;
    connectorEvent.end = {
      date: formattedEndDate
    };
  } else {
    connectorEvent.end = {
      dateTime: event.end,
      timeZone: event.timeZoneId,
    };
  }
  connectorEvent.description = event.description;
  connectorEvent.summary = event.summary;
  connectorEvent.location = event.location || (event.conferences && event.conferences.length && event.conferences[0].url) || '';
  connectorEvent.source =   {
    'url': `${window.location.origin}${eXo.env.portal.context}/${eXo.env.portal.portalName}/agenda?eventId=${event.id}`,
  };
  return connectorEvent;
}

function pad(n) {
  return n < 10 && `0${n}` || n;
}
