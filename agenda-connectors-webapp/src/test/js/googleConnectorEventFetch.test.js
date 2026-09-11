/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
import connector, {
  LISTING_SCOPES,
  WRITING_SCOPES,
} from '../../main/webapp/vue-app/agenda-connectors/google-connector/agendaGoogleConnector.js';

/**
 * What the fan-out does when one calendar of many refuses to answer, and
 * whether the promise the agenda waits on always settles.
 *
 * The connector is a singleton object, so every test rebuilds the few
 * members these paths touch rather than trusting what a previous test left.
 */

const PERIOD_START = new Date('2026-09-01T00:00:00Z');
const PERIOD_END = new Date('2026-09-30T23:59:59Z');

/**
 * Google's own hasGrantedAllScopes semantics: it answers from the scopes the
 * token itself declares. Modelling it any other way makes a test that cannot
 * tell what the connector reads from what it assumes.
 *
 * @returns {Object} a stand-in for google.accounts.oauth2
 */
function grantReader() {
  const granted = token => ((token && token.scope) || '').split(' ').filter(Boolean);
  return {
    hasGrantedAllScopes: (token, ...scopes) => scopes.every(scope => granted(token).includes(scope)),
    hasGrantedAnyScope: (token, ...scopes) => scopes.some(scope => granted(token).includes(scope)),
  };
}

/** A Google API error as gapi rejects it: an object carrying a status. */
function googleError(status) {
  return {status: status, result: {error: {code: status}}};
}

/** One timed Google event, the shape events.list answers with. */
function googleEvent(id, startHour) {
  return {
    id: id,
    summary: `event ${id}`,
    start: {dateTime: `2026-09-1${startHour}T10:00:00Z`},
    end: {dateTime: `2026-09-1${startHour}T11:00:00Z`},
  };
}

/**
 * Wires the connector over a stub account.
 *
 * @param {Object} eventsByCalendar id -> either an array of Google events or
 *        an error to reject with
 * @returns {Object} the call counters the assertions read
 */
function stubAccount(eventsByCalendar) {
  const counts = {calendarList: 0, events: {}};
  connector.calendarListing = null;
  // applyGrantedScopes() writes both of these, and the connector is a
  // singleton, so a test that narrowed the grant must not leak into the next.
  connector.canListCalendars = true;
  connector.canPush = false;
  connector.loadingCallback = jest.fn();
  // A normally-granted account: anything else would quietly divert these
  // tests onto the primary-only fallback, where a fixture whose calendar
  // happens to be named 'primary' still looks green.
  connector.cientOauth = grantReader();
  connector.authorize = jest.fn(() => Promise.resolve({access_token: 'renewed'}));
  connector.gapi = {
    client: {
      calendar: {
        calendarList: {
          list: () => {
            counts.calendarList++;
            return Promise.resolve({
              result: {
                items: Object.keys(eventsByCalendar).map(id => ({
                  id: id,
                  summary: id,
                  backgroundColor: '#039BE5',
                  accessRole: 'owner',
                })),
              },
            });
          },
        },
        events: {
          list: options => {
            const id = options.calendarId;
            counts.events[id] = (counts.events[id] || 0) + 1;
            const answer = eventsByCalendar[id];
            return answer instanceof Error || (answer && answer.status)
              ? Promise.reject(answer)
              : Promise.resolve({result: {items: answer}});
          },
        },
      },
    },
  };
  return counts;
}

describe('the consent asked for can actually list the calendars', () => {
  // calendar.events authorises events.list but NOT calendarList.list. The
  // accepted sets live in the module, so this asserts against the same list
  // the connector decides with rather than a copy that can drift from it.

  it('asks for a scope Google accepts for calendarList.list', () => {
    const asked = connector.requestedScopes().split(' ');
    expect(asked.some(scope => LISTING_SCOPES.includes(scope))).toBe(true);
  });

  it('still asks for a scope that authorises writing an event', () => {
    const asked = connector.requestedScopes().split(' ');
    expect(asked.some(scope => WRITING_SCOPES.includes(scope))).toBe(true);
  });
});

describe('an account whose grant cannot list calendars', () => {
  // An account connected before the listing scope was ever requested keeps
  // its narrower grant: a refresh token's scope is fixed at consent, and the
  // stored token is re-served on every page load. Such a user must keep the
  // agenda they had, not lose it.
  function stubNarrowGrant(eventsByCalendar) {
    const counts = stubAccount(eventsByCalendar);
    connector.applyGrantedScopes({
      access_token: 'old-narrow-grant',
      scope: connector.SCOPE_WRITE,
    });
    return counts;
  }

  it('reads the primary calendar rather than losing the agenda', () => {
    const counts = stubNarrowGrant({'primary': [googleEvent('a', 1)]});
    return connector.getEvents(PERIOD_START, PERIOD_END).then(events => {
      expect(events.map(event => event.id)).toEqual(['a']);
      // Nothing was asked of calendarList.list, so nothing could be refused.
      expect(counts.calendarList).toBe(0);
      expect(counts.events['primary']).toBe(1);
    });
  });

  it('says it cannot list, so the left panel does not offer the section', () => {
    stubNarrowGrant({'primary': [googleEvent('a', 1)]});
    expect(connector.canListCalendars).toBe(false);
  });

  it('still reports the write grant it does hold', () => {
    stubNarrowGrant({'primary': [googleEvent('a', 1)]});
    expect(connector.canPush).toBe(true);
  });

  it('stops claiming it cannot list once the account is disconnected', () => {
    stubNarrowGrant({'primary': [googleEvent('a', 1)]});
    expect(connector.canListCalendars).toBe(false);
    // agenda calls this on disconnect. The refusal belonged to the grant
    // being thrown away; leaving it set would make the next account inherit
    // it, and the fallback raises no error that could ever clear it.
    connector.resetPushAbility();
    expect(connector.canListCalendars).toBe(true);
    expect(connector.canPush).toBe(false);
  });

  it('treats a token that declares no scopes as unknown, not as ungranted', () => {
    // Every token refresh overwrites the stored blob, and RFC 6749 §5.1
    // makes scope optional on a refresh response when it is unchanged. A
    // token that says nothing must not convict the account of holding
    // nothing — which for canListCalendars would be permanent, since the
    // fallback it selects raises no error that could ever correct it.
    stubNarrowGrant({'primary': [googleEvent('a', 1)]});
    expect(connector.canListCalendars).toBe(false);
    expect(connector.canPush).toBe(true);
    connector.applyGrantedScopes({access_token: 'refreshed-without-scope'});
    expect(connector.canListCalendars).toBe(false);
    expect(connector.canPush).toBe(true);
  });

  it('does not let a scopeless refresh cancel a grant that could list', () => {
    const counts = stubAccount({
      'primary': [googleEvent('a', 1)],
      'shared@group.calendar.google.com': [googleEvent('b', 2)],
    });
    connector.applyGrantedScopes({
      access_token: 'full',
      scope: `${connector.SCOPE_READ} ${connector.SCOPE_WRITE}`,
    });
    connector.applyGrantedScopes({access_token: 'refreshed-without-scope'});
    return connector.getEvents(PERIOD_START, PERIOD_END).then(events => {
      expect(connector.canListCalendars).toBe(true);
      expect(counts.calendarList).toBe(1);
      expect(events.map(event => event.id)).toEqual(['a', 'b']);
    });
  });

  it('widens the flags from the token a renewal returns', () => {
    // The whole delivery rests on a capability derived from a grant and
    // recomputed whenever a token is obtained. In production the stored and
    // refreshed tokens always declare their scopes, so the renewal ladder
    // genuinely recomputes both flags — this is the only test that walks
    // that path end to end.
    const counts = stubNarrowGrant({
      'primary': googleError(401),
      'shared@group.calendar.google.com': [googleEvent('b', 2)],
    });
    expect(connector.canListCalendars).toBe(false);
    connector.authorize = jest.fn(() => {
      connector.gapi.client.calendar.events.list = options => {
        counts.events[options.calendarId] = (counts.events[options.calendarId] || 0) + 1;
        return Promise.resolve({result: {items: [googleEvent('a', 1)]}});
      };
      return Promise.resolve({
        access_token: 'renewed-and-widened',
        scope: `${connector.SCOPE_READ} ${connector.SCOPE_WRITE}`,
      });
    });
    return connector.getEvents(PERIOD_START, PERIOD_END).then(() => {
      expect(connector.canListCalendars).toBe(true);
      expect(connector.canPush).toBe(true);
      // It listed the account on the retry instead of staying on primary.
      expect(counts.calendarList).toBeGreaterThan(0);
    });
  });

  it('recognises the broader calendar scope as authorising both', () => {
    const counts = stubAccount({
      'primary': [googleEvent('a', 1)],
      'shared@group.calendar.google.com': [googleEvent('b', 2)],
    });
    // One grant that Google accepts for calendarList.list and for writing an
    // event, but which is neither of the two scopes this connector requests.
    connector.applyGrantedScopes({
      access_token: 'broad',
      scope: 'https://www.googleapis.com/auth/calendar',
    });
    expect(connector.canListCalendars).toBe(true);
    expect(connector.canPush).toBe(true);
    return connector.getEvents(PERIOD_START, PERIOD_END).then(events => {
      expect(counts.calendarList).toBe(1);
      expect(events.map(event => event.id)).toEqual(['a', 'b']);
    });
  });

  it('lists again once the grant includes the read scope', () => {
    const counts = stubAccount({'primary': [googleEvent('a', 1)]});
    connector.applyGrantedScopes({
      access_token: 'widened',
      scope: `${connector.SCOPE_READ} ${connector.SCOPE_WRITE}`,
    });
    return connector.getEvents(PERIOD_START, PERIOD_END)
      .then(() => expect(counts.calendarList).toBe(1));
  });
});

describe('getEvents when one calendar of several fails', () => {
  it('still shows the other calendars when one is throttled (403)', () => {
    stubAccount({
      'primary': [googleEvent('a', 1)],
      'shared@group.calendar.google.com': googleError(403),
      'birthdays': [googleEvent('b', 2)],
    });
    return connector.getEvents(PERIOD_START, PERIOD_END).then(events => {
      // The throttled calendar contributes nothing; it must not take the
      // other two down with it.
      expect(events.map(event => event.id)).toEqual(['a', 'b']);
    });
  });

  it('still shows the other calendars when one is not readable (404)', () => {
    stubAccount({
      'primary': [googleEvent('a', 1)],
      'revoked@group.calendar.google.com': googleError(404),
    });
    return connector.getEvents(PERIOD_START, PERIOD_END).then(events => {
      expect(events.map(event => event.id)).toEqual(['a']);
    });
  });

  it('lets a 401 through, and the renewed token reads the whole account', () => {
    // Two calendars on purpose: a single 'primary' fixture cannot tell a
    // real re-read from a fall back to the primary calendar alone.
    const counts = stubAccount({
      'primary': [googleEvent('a', 1)],
      'shared@group.calendar.google.com': [googleEvent('b', 2)],
    });
    const answerNormally = connector.gapi.client.calendar.events.list;
    let firstAttempt = true;
    connector.gapi.client.calendar.events.list = options => {
      if (firstAttempt) {
        firstAttempt = false;
        counts.events[options.calendarId] = (counts.events[options.calendarId] || 0) + 1;
        return Promise.reject(googleError(401));
      }
      return answerNormally(options);
    };
    return connector.getEvents(PERIOD_START, PERIOD_END).then(events => {
      // The 401 was not swallowed as "this calendar contributed nothing":
      // it reached the ladder, which renewed the token and tried again.
      expect(connector.authorize).toHaveBeenCalled();
      expect(counts.events['primary']).toBe(2);
      // Both calendars come back: the retry re-read the account rather than
      // silently dropping to primary.
      expect(events.map(event => event.id)).toEqual(['a', 'b']);
      expect(connector.canListCalendars).toBe(true);
    });
  });

  it('settles — rejects — when every renewal attempt still fails', () => {
    stubAccount({
      'primary': googleError(401),
    });
    connector.authorize = jest.fn(() => Promise.resolve({access_token: 'renewed'}));
    // A promise that never settles would time the test out here, which is
    // exactly the hang this pins: the spinner has to be released.
    return connector.getEvents(PERIOD_START, PERIOD_END).then(
      () => Promise.reject(new Error('expected the exhausted ladder to reject')),
      error => {
        expect(error.status).toBe(401);
        expect(connector.loadingCallback).toHaveBeenCalledWith(connector, false);
      });
  });

  it('settles when re-authorising answers no usable token', () => {
    stubAccount({
      'primary': googleError(401),
    });
    connector.authorize = jest.fn(() => Promise.resolve(null));
    return connector.getEvents(PERIOD_START, PERIOD_END).then(
      () => Promise.reject(new Error('expected a rejection, not a pending promise')),
      error => expect(error.status).toBe(401));
  });
});

describe('the calendar listing is fetched once and shared', () => {
  it('does not re-list the account on a second period navigation', () => {
    const counts = stubAccount({'primary': [googleEvent('a', 1)]});
    return connector.getEvents(PERIOD_START, PERIOD_END)
      .then(() => connector.getEvents(PERIOD_START, PERIOD_END))
      .then(() => {
        expect(counts.calendarList).toBe(1);
        expect(counts.events['primary']).toBe(2);
      });
  });

  it('shares one listing between the left panel and the grid', () => {
    const counts = stubAccount({'primary': [googleEvent('a', 1)]});
    return connector.listCalendars()
      .then(() => connector.getEvents(PERIOD_START, PERIOD_END))
      .then(() => expect(counts.calendarList).toBe(1));
  });

  it('answers callers that ask before the first listing has come back', () => {
    const counts = stubAccount({'primary': [googleEvent('a', 1)]});
    // Both start before either resolves: the promise, not the result, is what
    // is memoised, so this must still cost one request.
    return Promise.all([
      connector.listCalendars(),
      connector.getEvents(PERIOD_START, PERIOD_END),
    ]).then(() => expect(counts.calendarList).toBe(1));
  });

  it('does not remember a failed listing', () => {
    const counts = stubAccount({'primary': [googleEvent('a', 1)]});
    let firstCall = true;
    connector.gapi.client.calendar.calendarList.list = () => {
      counts.calendarList++;
      if (firstCall) {
        firstCall = false;
        return Promise.reject(googleError(500));
      }
      return Promise.resolve({result: {items: [{id: 'primary', summary: 'primary', backgroundColor: '#039BE5', accessRole: 'owner'}]}});
    };
    return connector.listCalendars()
      .catch(() => connector.listCalendars())
      .then(calendars => {
        // A transient failure must not be cached as "this account has no calendars".
        expect(calendars.map(calendar => calendar.id)).toEqual(['primary']);
        expect(counts.calendarList).toBe(2);
      });
  });

  it('re-lists whenever the panel asks, so agenda-refresh is not inert', () => {
    const counts = stubAccount({'primary': [googleEvent('a', 1)]});
    // A calendar created or renamed in Google mid-session has to appear
    // without a reload; the left panel gets that by asking again.
    return connector.listCalendars()
      .then(() => connector.listCalendars())
      .then(() => expect(counts.calendarList).toBe(2));
  });

  it('forgets the account it listed once it is disconnected', () => {
    const counts = stubAccount({'primary': [googleEvent('a', 1)]});
    connector.gapi.client.getToken = () => null;
    connector.user = null;
    global.eXo = {env: {portal: {context: 'portal', rest: 'rest'}}};
    global.fetch = jest.fn(() => Promise.resolve({ok: true}));
    // Observed through getEvents, which reads the published entry: if
    // disconnect() did not clear it, this would cost no second listing.
    return connector.listCalendars()
      .then(() => connector.disconnect())
      .then(() => connector.getEvents(PERIOD_START, PERIOD_END))
      .then(() => expect(counts.calendarList).toBe(2));
  });

  it('forgets the account it listed once another is connected', () => {
    const counts = stubAccount({'primary': [googleEvent('a', 1)]});
    connector.canPush = true;
    connector.credential = {email: 'someone@example.com'};
    connector.authenticate = jest.fn(() => Promise.resolve());
    return connector.listCalendars()
      .then(() => connector.connect(false))
      .then(() => connector.getEvents(PERIOD_START, PERIOD_END))
      .then(() => expect(counts.calendarList).toBe(2));
  });
});
