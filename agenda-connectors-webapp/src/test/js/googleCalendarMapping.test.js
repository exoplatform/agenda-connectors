/*
 * Copyright (C) 2026 eXo Platform SAS.
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
import {
  DEFAULT_CALENDAR_COLOR,
  mapCalendarListEntry,
  mapGoogleEvent,
  mergeEventLists,
} from '../../main/webapp/vue-app/agenda-connectors/google-connector/googleCalendarMapping.js';

describe('mapCalendarListEntry', () => {
  it('maps a calendarList entry to the {id, name, color, readOnly} contract', () => {
    const calendar = mapCalendarListEntry({
      id: 'family@group.calendar.google.com',
      summary: 'Family',
      backgroundColor: '#D50000',
      accessRole: 'owner',
    });
    expect(calendar).toEqual({
      id: 'family@group.calendar.google.com',
      name: 'Family',
      color: '#D50000',
      readOnly: false,
    });
  });

  it('prefers the user\'s own rename of a shared calendar over the owner\'s name', () => {
    const calendar = mapCalendarListEntry({
      id: 'team@group.calendar.google.com',
      summary: 'Team calendar',
      summaryOverride: 'My team',
      backgroundColor: '#33B679',
      accessRole: 'reader',
    });
    expect(calendar.name).toBe('My team');
  });

  it('falls back to the id when the entry has no name at all', () => {
    const calendar = mapCalendarListEntry({
      id: 'anais.francois@gmail.com',
      backgroundColor: '#039BE5',
      accessRole: 'owner',
    });
    expect(calendar.name).toBe('anais.francois@gmail.com');
  });

  it('never leaves the colour empty — a colourless calendar would paint invisible events', () => {
    const calendar = mapCalendarListEntry({
      id: 'a@b.c',
      summary: 'No colour',
      accessRole: 'owner',
    });
    expect(calendar.color).toBe(DEFAULT_CALENDAR_COLOR);
    expect(calendar.color.toUpperCase()).not.toBe('#FFFFFF');
  });

  it('marks reader and freeBusyReader access as read-only, writer and owner as writable', () => {
    const roleOf = accessRole => mapCalendarListEntry({id: 'a@b.c', summary: 'x', accessRole}).readOnly;
    expect(roleOf('reader')).toBe(true);
    expect(roleOf('freeBusyReader')).toBe(true);
    expect(roleOf('writer')).toBe(false);
    expect(roleOf('owner')).toBe(false);
  });
});

describe('mapGoogleEvent', () => {
  const calendar = {
    id: 'family@group.calendar.google.com',
    name: 'Family',
    color: '#D50000',
    readOnly: false,
  };

  it('tags the event with the calendar it came from and that calendar\'s colour', () => {
    const event = mapGoogleEvent({
      id: 'evt1',
      summary: 'Dinner',
      start: {dateTime: '2026-08-25T19:00:00+02:00'},
      end: {dateTime: '2026-08-25T21:00:00+02:00'},
    }, calendar);
    expect(event.calendarId).toBe(calendar.id);
    expect(event.color).toBe('#D50000');
    expect(event.type).toBe('remoteEvent');
    expect(event.name).toBe('Dinner');
    expect(event.allDay).toBe(false);
    expect(event.start).toBe('2026-08-25T19:00:00+02:00');
    expect(event.end).toBe('2026-08-25T21:00:00+02:00');
  });

  it('recognises an all-day event and subtracts the day Google adds to its end', () => {
    const event = mapGoogleEvent({
      id: 'evt2',
      summary: 'Holiday',
      start: {date: '2026-08-25'},
      end: {date: '2026-08-26'},
    }, calendar);
    expect(event.allDay).toBe(true);
    expect(event.start).toBe('2026-08-25');
    expect(event.end).toEqual(new Date('2026-08-25'));
  });

  it('does not mutate the Google event it was given', () => {
    const gEvent = {
      id: 'evt3',
      summary: 'Untouched',
      start: {dateTime: '2026-08-25T09:00:00+02:00'},
      end: {dateTime: '2026-08-25T10:00:00+02:00'},
    };
    mapGoogleEvent(gEvent, calendar);
    expect(gEvent.start).toEqual({dateTime: '2026-08-25T09:00:00+02:00'});
    expect(gEvent.color).toBeUndefined();
  });
});

describe('mergeEventLists', () => {
  const eventAt = (id, start) => ({id, start, end: start});

  it('flattens the per-calendar lists into one ordered by start', () => {
    const merged = mergeEventLists([
      [eventAt('b', '2026-08-25T14:00:00+02:00')],
      [eventAt('a', '2026-08-25T09:00:00+02:00'), eventAt('c', '2026-08-26T09:00:00+02:00')],
    ]);
    expect(merged.map(event => event.id)).toEqual(['a', 'b', 'c']);
  });

  it('drops the second copy of an event shared between two calendars', () => {
    const merged = mergeEventLists([
      [eventAt('shared', '2026-08-25T09:00:00+02:00')],
      [eventAt('shared', '2026-08-25T09:00:00+02:00')],
    ]);
    expect(merged).toHaveLength(1);
  });

  it('keeps the first copy, so its calendar tag and colour win', () => {
    const first = Object.assign(eventAt('shared', '2026-08-25T09:00:00+02:00'), {calendarId: 'one', color: '#111111'});
    const second = Object.assign(eventAt('shared', '2026-08-25T09:00:00+02:00'), {calendarId: 'two', color: '#222222'});
    const merged = mergeEventLists([[first], [second]]);
    expect(merged[0].calendarId).toBe('one');
  });

  it('answers an empty list for missing input rather than failing', () => {
    expect(mergeEventLists(null)).toEqual([]);
    expect(mergeEventLists([null, []])).toEqual([]);
  });
});
