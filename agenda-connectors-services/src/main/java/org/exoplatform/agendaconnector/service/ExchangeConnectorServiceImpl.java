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
import org.exoplatform.agendaconnector.storage.ExchangeConnectorStorage;


public class ExchangeConnectorServiceImpl implements ExchangeConnectorService {

  private ExchangeConnectorStorage exchangeConnectorStorage;

  public ExchangeConnectorServiceImpl(ExchangeConnectorStorage exchangeConnectorStorage) {
    this.exchangeConnectorStorage = exchangeConnectorStorage;
  }

  @Override
  public void createExchangeSetting(ExchangeUserSetting exchangeUserSetting, long userIdentityId) {
    if (userIdentityId <= 0) {
      throw new IllegalArgumentException("User identity id is mandatory");
    }
    if (exchangeUserSetting == null) {
      throw new IllegalArgumentException("Exchange user setting is empty");
    }
    exchangeConnectorStorage.createExchangeSetting(exchangeUserSetting, userIdentityId);
  }

  @Override
  public ExchangeUserSetting getExchangeSetting(long userIdentityId) {
    return exchangeConnectorStorage.getExchangeSetting(userIdentityId);
  }
}
