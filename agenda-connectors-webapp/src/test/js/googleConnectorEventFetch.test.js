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
import connector from '../../main/webapp/vue-app/agenda-connectors/google-connector/agendaGoogleConnector.js';

/**
 * What the fan-out does when one calendar of many refuses to answer, and
 * whether the promise the agenda waits on always settles.
 *
 * The connector is a singleton object, so every test rebuilds the few
 * members these paths touch rather than trusting what a previous test left.
 */

const PERIOD_START = new Date('2026-09-01T00:00:00Z');
const PERIOD_END = new Date('2026-09-30T23:59:59Z');

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
  connector.loadingCallback = jest.fn();
  connector.cientOauth = {hasGrantedAllScopes: () => false};
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
  // calendar.events authorises events.list but NOT calendarList.list, which
  // takes calendar.readonly, calendar, calendar.calendarlist or
  // calendar.calendarlist.readonly. Asking for events alone made the whole
  // multi-calendar read fail for a first-time consent.
  const LISTING_SCOPES = [
    'https://www.googleapis.com/auth/calendar.readonly',
    'https://www.googleapis.com/auth/calendar',
    'https://www.googleapis.com/auth/calendar.calendarlist',
    'https://www.googleapis.com/auth/calendar.calendarlist.readonly',
  ];

  it('asks for a scope Google accepts for calendarList.list', () => {
    const asked = connector.requestedScopes().split(' ');
    expect(asked.some(scope => LISTING_SCOPES.includes(scope))).toBe(true);
  });

  it('still asks for the event scope canPush is computed from', () => {
    expect(connector.requestedScopes().split(' ')).toContain(connector.SCOPE_WRITE);
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

  it('lets a 401 through, so the caller still gets to renew the token', () => {
    const counts = stubAccount({'primary': [googleEvent('a', 1)]});
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
      expect(events.map(event => event.id)).toEqual(['a']);
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
