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

/**
 * Pure mapping between what the Google Calendar API answers and what agenda
 * expects from a connector — kept free of gapi so it can be unit-tested
 * without a browser or a Google account.
 */

/**
 * The colour used when a calendarList entry carries none. Google populates
 * backgroundColor on every entry in practice, so this is a guard, not a
 * feature: it must simply never be white — a white event on the white grid
 * is an invisible event, which is the very bug real colours fix here.
 * The value is Google Calendar's own default event colour ("Peacock").
 */
export const DEFAULT_CALENDAR_COLOR = '#039BE5';

/**
 * Google access roles that cannot write events. Anything else — writer,
 * owner — can.
 */
const READ_ONLY_ACCESS_ROLES = ['reader', 'freeBusyReader'];

/**
 * Maps one Google calendarList entry to the calendar descriptor agenda's
 * left panel expects from any connector — the same shape the CalDAV
 * connector resolves listCalendars() with: an identity, a name, a colour
 * that is always usable, and whether the calendar may be written to.
 *
 * The identity is Google's calendar id (the account's email for the primary
 * calendar), never the display name: a user renaming a calendar must not
 * detach whatever agenda associated with it. The name prefers
 * summaryOverride — the rename the user applied to a shared calendar in
 * their own Google UI — over the owner's summary.
 *
 * @param {Object} entry one item of a calendarList.list response
 * @returns {Object} {id, name, color, readOnly} as agenda reads them
 */
export function mapCalendarListEntry(entry) {
  return {
    id: entry.id,
    name: entry.summaryOverride || entry.summary || entry.id,
    color: entry.backgroundColor || DEFAULT_CALENDAR_COLOR,
    readOnly: READ_ONLY_ACCESS_ROLES.includes(entry.accessRole),
  };
}

/**
 * Maps one Google event to the shape agenda's grid expects, tagging it with
 * the calendar it came from: calendarId is what the left panel's
 * per-calendar checkboxes filter on, and color is the calendar's real
 * colour — replacing the former hardcoded '#FFFFFF' that painted a white
 * event on the white grid.
 *
 * The date handling is the connector's historical behaviour, unchanged: an
 * all-day event is recognised by start.date, and Google answers all-day
 * events with one day added to the end date, which is subtracted back.
 *
 * @param {Object} gEvent one item of an events.list response
 * @param {Object} calendar the mapped calendar the event belongs to
 * @returns {Object} the event, completed with agenda's fields
 */
export function mapGoogleEvent(gEvent, calendar) {
  const event = Object.assign({}, gEvent);
  event.allDay = !!gEvent.start.date;
  event.start = gEvent.start.dateTime || gEvent.start.date;
  // Google api returns all day event with one day added for end date.
  const endDate = new Date(gEvent.end.date);
  endDate.setDate(endDate.getDate() - 1);
  event.end = event.allDay ? endDate : gEvent.end.dateTime;
  event.name = gEvent.summary;
  event.type = 'remoteEvent';
  event.calendarId = calendar.id;
  event.color = calendar.color;
  return event;
}

/**
 * Merges the per-calendar event lists into the single list agenda consumes.
 *
 * Deduplicated by event id, keeping the first occurrence: a shared calendar
 * can surface the same underlying Google event — same id — that the user's
 * own calendar already holds, and each copy would otherwise land on the grid
 * twice. Sorted by start, because the single-calendar implementation asked
 * Google for startTime order and callers may rely on it; a merge of
 * per-calendar answers has to restore it.
 *
 * @param {Array} eventLists one array of mapped events per calendar
 * @returns {Array} one flat, deduplicated, start-ordered list
 */
export function mergeEventLists(eventLists) {
  const seenIds = [];
  const events = [];
  (eventLists || []).forEach(list => (list || []).forEach(event => {
    if (!event.id || !seenIds.includes(event.id)) {
      seenIds.push(event.id);
      events.push(event);
    }
  }));
  return events.sort((first, second) => new Date(first.start).getTime() - new Date(second.start).getTime());
}
