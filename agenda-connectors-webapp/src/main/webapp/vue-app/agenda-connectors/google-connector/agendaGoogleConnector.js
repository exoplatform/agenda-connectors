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

/**
 * The calendar eXo copies of meetings are pushed to. Deliberately not derived
 * from the calendar listing: reading spans all of the user's calendars, but
 * the write destination stays the account's primary calendar, where every
 * copy pushed so far already lives — pointing new copies elsewhere would
 * strand the existing ones. A future decision to change the destination
 * belongs here, and only here.
 */
const PUSH_CALENDAR_ID = 'primary';

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
  authorize(refresh) {
    return new Promise((resolve, reject) => {
      try {
        this.codeClient.callback = (response) => {
          if (response && response.code) {
            return requestToken(response.code, response.scope, window.location.origin)
              .then(tokenResponse => {
                if (tokenResponse && tokenResponse.access_token) {
                  this.gapi.client.setToken(tokenResponse);
                  resolve(tokenResponse);
                }
              });
          }
        };
        if (refresh) {
          return refreshToken().then(refreshTokenResponse => {
            if (refreshTokenResponse && refreshTokenResponse.access_token) {
              this.gapi.client.setToken(refreshTokenResponse);
              this.canPush = this.cientOauth.hasGrantedAllScopes(refreshTokenResponse, this.SCOPE_WRITE);
              resolve(refreshTokenResponse);
            }
          });
        } else if (this.user) {
          return getStoredToken().then(tokenResponse => {
            if (tokenResponse && tokenResponse.access_token) {
              this.gapi.client.setToken(tokenResponse);
              this.canPush = this.cientOauth.hasGrantedAllScopes(tokenResponse, this.SCOPE_WRITE);
              this.gapi.client.setToken(tokenResponse);
              resolve(tokenResponse);
            }
          }).catch((error) => {
            if (error.status === 404) {
              this.codeClient.requestCode();
            }
          });
        } else {
          this.codeClient.requestCode();
        }
      } catch (err) {
        reject(err);
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
      return this.authorize().then(tokenResponse => {
        if (tokenResponse && tokenResponse.access_token) {
          this.canPush = this.cientOauth.hasGrantedAllScopes(tokenResponse, this.SCOPE_WRITE);
          return this.authenticate().then(() => {
            return new Promise((resolve, reject) => {
              if (this.credential) {
                const userEmail = this.credential.email;
                resolve(userEmail);
              } else {
                reject();
              }
            });
          });
        }
      });
    } else {
      return this.authenticate().then(() => {
        return new Promise((resolve, reject) => {
          if (this.credential) {
            resolve(this.credential.email);
          } else {
            reject();
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
  getEvents(periodStartDate, periodEndDate) {
    if (this.gapi && this.gapi.client && this.gapi.client.calendar) {
      this.loadingCallback(this, true);
      return new Promise((resolve, reject) => {
        retrieveEvents(this, periodStartDate, periodEndDate)
          .then(gEvents => resolve(gEvents))
          .catch(e => {
            if (e.status === 403 || e.status === 401) {
              return this.authorize().then((tokenResponse) => {
                if (tokenResponse && tokenResponse.access_token) {
                  this.canPush = this.cientOauth.hasGrantedAllScopes(tokenResponse, this.SCOPE_WRITE);
                  retrieveEvents(this, periodStartDate, periodEndDate)
                    .then(gEvents => resolve(gEvents))
                    .catch((e) => {
                      if (e.status === 403 || e.status === 401) {
                        return this.authorize(true).then(() => {
                          retrieveEvents(this, periodStartDate, periodEndDate)
                            .then(gEvents => resolve(gEvents));
                        });
                      } else {
                        this.loadingCallback(this, false);
                        reject(e);
                      }
                    });
                }
              });
            } else {
              this.loadingCallback(this, false);
              reject(e);
            }
          });
      }).finally(() => this.loadingCallback(this, false));
    } else {
      return Promise.resolve(null);
    }
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
        if (error.status === 403 || error.status === 401) {
          return this.authorize().then(tokenResponse => {
            if (tokenResponse && tokenResponse.access_token) {
              this.canPush = this.cientOauth.hasGrantedAllScopes(tokenResponse, this.SCOPE_WRITE);
            }
            return retrieveCalendarList(this);
          });
        }
        throw error;
      })
      .then(entries => entries.map(mapCalendarListEntry));
  },
  deleteEvent(event, connectorRecurringEventId) {
    return this.saveEvent(event, connectorRecurringEventId, true);
  },
  pushEvent(event, connectorRecurringEventId) {
    return this.saveEvent(event, connectorRecurringEventId, false);
  },
  saveEvent(event, connectorRecurringEventId, deleteEvent) {
    if (this.gapi) {
      this.pushing = true;
      return new Promise((resolve, reject) => {
        pushEventToGoogle(this, event, connectorRecurringEventId, deleteEvent)
          .then(gEvent => {
            resolve(gEvent);
          }).catch(error => {
            if (error.status === 403 || error.status === 401) {
              return this.authorize().then(() => {
                pushEventToGoogle(this, event, connectorRecurringEventId, deleteEvent)
                  .then(gEvent => {
                    resolve(gEvent);
                  }).catch(error => {
                    if (error.status === 403 || error.status === 401) {
                      return this.authorize(true).then(() => {
                        pushEventToGoogle(this, event, connectorRecurringEventId, deleteEvent)
                          .then(gEvent => {
                            resolve(gEvent);
                          });
                      });
                    } else {
                      this.loadingCallback(this, false);
                      reject(error);
                    }
                  });
              });
            } else {
              this.loadingCallback(this, false);
              reject(error);
            }
          });
      }).finally(() => this.pushing = false);
    }
    return Promise.reject(new Error('Not connected'));
  },
};

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
function retrieveCalendarList(connector, pageToken, accumulated) {
  const options = {};
  if (pageToken) {
    options.pageToken = pageToken;
  }
  return connector.gapi.client.calendar.calendarList.list(options)
    .then(response => {
      const result = response.result || {};
      const entries = (accumulated || []).concat(result.items || []);
      return result.nextPageToken
        ? retrieveCalendarList(connector, result.nextPageToken, entries)
        : entries;
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
 * @param {Date}
 *          periodStartDate Start date of period of events to retrieve
 * @param {Date}
 *          periodEndDate End date of period of events to retrieve
 * @returns {Promise} a promise with list of Google events
 */
function retrieveEvents(connector, periodStartDate, periodEndDate) {
  return retrieveCalendarList(connector)
    .then(entries => entries.map(mapCalendarListEntry))
    .then(calendars => Promise.all(calendars.map(calendar =>
      connector.gapi.client.calendar.events.list({
        'calendarId': calendar.id,
        'timeMin': periodStartDate,
        'timeMax': periodEndDate,
        'singleEvents': true,
        'orderBy': 'startTime'
      }).then(events => (events.result.items || []).map(event => mapGoogleEvent(event, calendar)))
        .catch(error => {
          if (error.status === 403 || error.status === 401) {
            throw error;
          }
          console.error(`cannot retrieve the events of Google calendar ${calendar.id}`, error);
          return [];
        })
    )))
    .then(eventLists => {
      connector.loadingCallback(connector, false);
      return mergeEventLists(eventLists);
    });
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
    connector.identity = google.accounts.id;
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
    connector.gapi = gapi;
    connector.gapi.load('client', function() {
      gapi.client.init({
        discoveryDocs: connector.DISCOVERY_DOCS,
      }).then(function () {
        checkUserStatus(connector);
        connector.cientOauth = google.accounts.oauth2;
        connector.codeClient = connector.cientOauth.initCodeClient({
          client_id: connector.CLIENT_ID,
          scope: connector.SCOPE_WRITE,
          ux_mode: 'popup',
          error_callback: (error) => {
            connector.loadingCallback(connector, false);
            connector.connectionStatusChangedCallback(connector, false, error);
          }
        });
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
