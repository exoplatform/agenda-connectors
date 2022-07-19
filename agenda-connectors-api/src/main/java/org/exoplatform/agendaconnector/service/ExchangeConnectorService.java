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
   * @param exchangeUserSetting {@link ExchangeUserSetting} object to create
   * @param userIdentityId User identity creating the exchange user setting
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
   * @param userIdentityId User identity pushing the event to exchange agenda
   * @param event Event to be pushed to exchange agenda
   * @param userTimeZone User time zone
   * @throws IllegalAccessException when the user is not authorized to push event in exchange agenda
   */
  void pushEventToExchange(long userIdentityId, EventEntity event, ZoneId userTimeZone) throws IllegalAccessException;

  /**
   * Gets exchange event from exchange agenda by identifier.
   * @param eventId agenda {@link Event} identifier
   * @param userIdentityId User identity getting exchange event from exchange agenda
   * @param userTimeZone User time zone
   * @throws IllegalAccessException when the user is not authorized to get event from exchange agenda
   */
  EventEntity getExchangeEventById(long eventId, long userIdentityId, ZoneId userTimeZone) throws IllegalAccessException;

  /**
   * Deletes exchange event from exchange agenda.
   * @param userIdentityId User identity deleting the exchange event from exchange agenda
   * @param eventId agenda {@link Event} identifier
   * @throws IllegalAccessException when the user is not authorized to delete event from exchange agenda
   */
  void deleteExchangeEvent(long userIdentityId, long eventId) throws IllegalAccessException;

  /**
   * Gets deleted exchange event from exchange agenda.
   * @param userIdentityId User identity getting deleted exchange event from exchange agenda
   * @param userTimeZone User time zone
   * @throws IllegalAccessException when the user is not authorized to get deleted exchange event from exchange agenda
   */
  EventEntity getDeletedExchangeEvent(long userIdentityId, ZoneId userTimeZone) throws IllegalAccessException;

}
