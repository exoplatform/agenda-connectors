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
package org.exoplatform.agendaconnector.service;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agendaconnector.model.ExchangeUserSetting;

import java.time.ZoneId;
import java.util.List;

public interface ExchangeConnectorService {

  /**
   * Creates a new exchange user setting
   *
   * @param userIdentityId User identity creating the exchange user setting
   * @param exchangeUserSetting {@link ExchangeUserSetting} object to create
   * @throws IllegalAccessException when the user is not authorized to create exchange setting
   */
  void createExchangeSetting(ExchangeUserSetting exchangeUserSetting, long userIdentityId) throws IllegalAccessException;

  /**
   * Retrieves exchange user setting by its technical user identity identifier.
   *
   * @param userIdentityId User identity getting the exchange user setting
   * @return A {@link ExchangeUserSetting} object
   */
  ExchangeUserSetting getExchangeSetting(long userIdentityId);
  
  /**
   * Deletes an exchange user setting
   *
   * @param userIdentityId User identity deleting his exchange user setting
   */
  void deleteExchangeSetting(long userIdentityId);

  /**
   * Retrieves remote user exchange events.
   *
   * @param userIdentityId User identity retrieving exchange events
   * @param start Start date of retrieved events
   * @param end End date of retrieved events
   * @param userTimeZone User time zone
   * @return {@link List} of {@link EventEntity}
   * @throws IllegalAccessException when the user is not authorized to get remote user exchange events
   */
  List<EventEntity> getExchangeEvents(long userIdentityId,
                                      String start,
                                      String end,
                                      ZoneId userTimeZone) throws IllegalAccessException;


  /**
   * Pushes event in exchange agenda.
   *
   * @param identityId User identity pushing the event to exchange agenda
   * @param event Event to be pushed to exchange agenda
   * @param userTimeZone User time zone
   * @throws IllegalAccessException when the user is not authorized to push event in exchange agenda
   */
  void pushEventToExchange(long identityId, EventEntity event, ZoneId userTimeZone) throws IllegalAccessException;

  /**
   * get remote event from exchange agenda.
   * @param eventId agenda {@link Event} identifier
   * @param identityId User identity to get the event from exchange agenda
   * @param userTimeZone User time zone
   * @throws IllegalAccessException when the user is not authorized to get event from exchange agenda
   */
  EventEntity getRemoteEventById(long eventId, long identityId, ZoneId userTimeZone) throws IllegalAccessException;

  /**
   * delete remote event from exchange agenda.
   * @param identityId User identity deleting the event from exchange agenda
   * @param eventId agenda {@link Event} identifier
   * @throws IllegalAccessException when the user is not authorized to delete event from exchange agenda
   */
  void deleteEventFromExchange(long identityId, long eventId) throws IllegalAccessException;

  /**
   * get removed event from exchange agenda.
   * @param identityId User identity to get the removed event from exchange agenda
   * @param userTimeZone User time zone
   * @throws IllegalAccessException when the user is not authorized to get removed event from exchange agenda
   */
  EventEntity getDeletedEvent(long identityId, ZoneId userTimeZone) throws IllegalAccessException;

}
