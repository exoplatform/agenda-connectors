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
 * Attaches a third party SDK handle (gapi, Google Identity, MSAL...) to a
 * connector as a non enumerable property, so Vue never walks it.
 *
 * Connectors live in the Agenda applications reactive data, and these handles
 * reference the cross origin frames their provider opens: reading a named
 * property of such a frame throws a SecurityError, which escapes Watcher.run()
 * and wedges Vue's scheduler for the whole page (EXO-90245).
 *
 * @param {Object} connector connector registered in extensionRegistry
 * @param {String} name property holding the SDK handle
 * @param {Object} value the SDK handle to attach
 * @returns {void}
 */
export function defineSdkHandle(connector, name, value) {
  Object.defineProperty(connector, name, {
    value: value,
    enumerable: false,
    writable: true,
    configurable: true,
  });
}
