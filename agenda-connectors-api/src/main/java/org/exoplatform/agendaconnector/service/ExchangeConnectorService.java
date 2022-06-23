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

import org.exoplatform.agendaconnector.model.ExchangeUserSetting;

public interface ExchangeConnectorService {

  /**
   * Creates a new exchange user setting
   *
   * @param userIdentityId User identity getting the exchange user setting
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
   * @throws IllegalAccessException when the user is not authorized to delete exchange user setting
   */
  void deleteExchangeSetting(long userIdentityId);
}
