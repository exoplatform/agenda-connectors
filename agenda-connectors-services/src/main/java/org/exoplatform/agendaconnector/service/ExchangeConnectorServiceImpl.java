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
import java.time.ZonedDateTime;
import java.util.*;

import microsoft.exchange.webservices.data.core.PropertyBag;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.search.LogicalOperator;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.core.service.schema.AppointmentSchema;
import microsoft.exchange.webservices.data.property.definition.PropertyDefinition;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.ItemView;
import microsoft.exchange.webservices.data.search.filter.SearchFilter;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.util.AgendaDateUtils;
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
      connectExchangeService(exchangeService, exchangeUserSetting);
    } catch (Exception e) {
      throw new IllegalAccessException("Can not connect to exchange server");
    }
  }

  @Override
  public List<EventEntity> getEvents(long userIdentityId,
                                     String start,
                                     String end,
                                     ZoneId userTimeZone) throws IllegalAccessException {
    ExchangeUserSetting exchangeUserSetting = getExchangeSetting(userIdentityId);
    ExchangeService exchangeService = new ExchangeService(ExchangeVersion.Exchange2010_SP2);
    try{
      exchangeService = connectExchangeService(exchangeService, exchangeUserSetting);
      ItemView view = new ItemView(100);

      ZonedDateTime filterStartDate = AgendaDateUtils.parseAllDayDateToZonedDateTime(start);
      ZonedDateTime filterEndDate = AgendaDateUtils.parseAllDayDateToZonedDateTime(end).plusDays(1);

      SearchFilter fromFilter = new SearchFilter.IsGreaterThanOrEqualTo(AppointmentSchema.Start,
                                                                        AgendaDateUtils.toDate(filterStartDate));
      SearchFilter toFilter = new SearchFilter.IsLessThanOrEqualTo(AppointmentSchema.End, AgendaDateUtils.toDate(filterEndDate));
      SearchFilter inRangeFilter = new SearchFilter.SearchFilterCollection(LogicalOperator.And, fromFilter, toFilter);
      FindItemsResults<Item> findResults;
      findResults = exchangeService.findItems(WellKnownFolderName.Calendar, inRangeFilter, view);
      List<Item> items = findResults.getItems();
      List<EventEntity> eventEntities = new ArrayList<>();
      for (int i = 0; i < items.size(); i++) {
        EventEntity event = new EventEntity();
        event.setSummary(items.get(i).getSubject());
        PropertyBag properties = items.get(i).getPropertyBag();
        Set<Map.Entry<PropertyDefinition, Object>> entrySet = properties.getProperties().entrySet();

        Date startDate = (Date) Objects.requireNonNull(entrySet.stream()
                                                               .filter(entry -> entry.getKey().getUri().equals("calendar:Start"))
                                                               .findFirst()
                                                               .orElse(null))
                                       .getValue();
        ZonedDateTime startDateTime = AgendaDateUtils.fromDate(startDate).withZoneSameInstant(userTimeZone);
        String startDateRFC3339 = AgendaDateUtils.toRFC3339Date(startDateTime);
        event.setStart(startDateRFC3339);

        Date endDate = (Date) Objects.requireNonNull(entrySet.stream()
                                                             .filter(entry -> entry.getKey().getUri().equals("calendar:End"))
                                                             .findFirst()
                                                             .orElse(null))
                                     .getValue();
        ZonedDateTime endDateTime = AgendaDateUtils.fromDate(endDate).withZoneSameInstant(userTimeZone);
        String endDateRFC3339 = AgendaDateUtils.toRFC3339Date(endDateTime);
        event.setEnd(endDateRFC3339);

        eventEntities.add(event);
      }
      return eventEntities;
    } catch (Exception e) {
      throw new IllegalAccessException("Can not connect to exchange server");
    }
  }
    
  private ExchangeService connectExchangeService(ExchangeService exchangeService,
                                                 ExchangeUserSetting exchangeUserSetting) throws Exception {
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
    return exchangeService;

  }
}
