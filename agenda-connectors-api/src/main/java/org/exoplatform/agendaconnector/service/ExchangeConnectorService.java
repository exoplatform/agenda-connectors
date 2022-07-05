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
   */
  void createExchangeSetting(ExchangeUserSetting exchangeUserSetting, long userIdentityId);

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
   * Retrieves list of remote events from Exchange connector.
   *
   * @param userTimeZone User time zone
   * @return {@link List} of {@link EventEntity}
   */
  List<EventEntity> getEvents(long userIdentityId,String start, String end, ZoneId userTimeZone) throws IllegalAccessException;

  /** 
   * Connects to exchange server by exchange user setting.
   *
   * @param exchangeUserSetting {@link ExchangeUserSetting} object to connect to exchange server
   * @throws IllegalAccessException when the connection to exchange server is not established
   */
  void connectExchangeSetting(ExchangeUserSetting exchangeUserSetting) throws IllegalAccessException;
}
