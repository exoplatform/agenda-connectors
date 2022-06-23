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
export default {
  name: 'agenda.exchangeCalendar',
  description: 'agenda.exchangeCalendar.description',
  avatar: '/agenda-connectors/skin/images/MicrosoftExchange.png',
  isOauth: false,
  canConnect: true,
  canPush: false,
  initialized: true,
  isSignedIn: false,
  pushing: false,
  rank: 30,
  connect() {
    return new Promise((resolve, reject) => {
      document.dispatchEvent(new CustomEvent('open-connector-settings-drawer'));
      document.addEventListener('test-connection',(testConnection) => {
        if (testConnection.detail) {
          resolve('user connected');
        }
        else {
          reject('connection canceled');
        }
      });
    });
  },
  disconnect() {
    return Promise.resolve(null);
  }
};