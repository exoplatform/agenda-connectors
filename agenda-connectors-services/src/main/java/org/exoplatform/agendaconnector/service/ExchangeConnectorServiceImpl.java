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

import java.net.URI;
import java.time.ZoneId;
import java.util.List;

import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agendaconnector.model.ExchangeUserSetting;
import org.exoplatform.agendaconnector.storage.ExchangeConnectorStorage;
import org.exoplatform.agendaconnector.utils.ExchangeConnectorUtils;

import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.credential.ExchangeCredentials;
import microsoft.exchange.webservices.data.credential.WebCredentials;


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
  
  @Override
  public void deleteExchangeSetting(long userIdentityId) {
    exchangeConnectorStorage.deleteExchangeSetting(userIdentityId);
  }  
  
  @Override
  public void connectExchangeSetting(ExchangeUserSetting exchangeUserSetting) throws IllegalAccessException {
    try (ExchangeService exchangeService = new ExchangeService(ExchangeVersion.Exchange2010_SP2)) {
      exchangeService.setTimeout(300000);
      String exchangeDomain = exchangeUserSetting.getDomainName();
      String exchangeUsername = exchangeUserSetting.getUsername();
      String exchangePassword = exchangeUserSetting.getPassword();
      String exchangeServerURL = System.getProperty("exo.exchange.server.url");
      ExchangeCredentials credentials = null;
      if (exchangeDomain != null) {
        credentials = new WebCredentials(exchangeUsername, exchangePassword, exchangeDomain);
      } else {
        credentials = new WebCredentials(exchangeUsername, exchangePassword);
      }
      exchangeService.setCredentials(credentials);
      exchangeService.setUrl(new URI(exchangeServerURL + ExchangeConnectorUtils.EWS_URL));
      exchangeService.getInboxRules();
    } catch (Exception e) {
      throw new IllegalAccessException("Can not connect to exchange server");
    }
  }

  @Override
   public List<EventEntity> getEvents(String start, String end, ZoneId userTimeZone) {
    return ExchangeConnectorStorage.getEvents(start, end, userTimeZone);
  }
}
