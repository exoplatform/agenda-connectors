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
import * as agendaExchangeService from '../js/agendaExchangeService.js';
export default {
  name: 'agenda.exchangeCalendar',
  description: 'agenda.exchangeCalendar.description',
  avatar: '/agenda-connectors/skin/images/MicrosoftExchange.png',
  isOauth: false,
  canConnect: true,
  canPush: true,
  initialized: true,
  isSignedIn: true,
  pushing: false,
  rank: 30,
  connect(askWriteAccess) {
    if (askWriteAccess) {
      return new Promise((resolve, reject) => {
        document.dispatchEvent(new CustomEvent('open-connector-settings-drawer'));
        document.addEventListener('test-connection', (settings) => {
          if (settings.detail) {
            resolve(settings.detail.username);
          } else {
            reject('connection canceled');
          }
        });
      });
    }
  },
  disconnect() {
    return new Promise((resolve, reject) => {
      return agendaExchangeService.deleteExchangeSetting().then((respStatus) => {
        if (respStatus === 200) {
          return resolve(null);
        }
      }).catch(e => {
        return reject(e);
      });
    });
  },

  getEvents(periodStartDate, periodEndDate){
    return agendaExchangeService.getExchangeEvents(periodStartDate, periodEndDate)
      .then(events => {
        events.forEach(event => {
          event.type = 'remoteEvent';
          event.color = '#FFFFFF';
        });
        return events;
      });
  },
  pushEvent(event){
    const connectorEvent = {
      id: event.id,
      summary: event.summary,
      start: event.start,
      end: event.end,
      remoteProviderName: this.name,
      remoteProviderId: 3,
    };
    agendaExchangeService.pushEventToExchange(connectorEvent);
    return connectorEvent;
  }
  
};
