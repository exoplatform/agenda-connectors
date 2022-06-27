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
package org.exoplatform.agendaconnector.storage;

import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agendaconnector.model.ExchangeUserSetting;
import org.exoplatform.agendaconnector.utils.ExchangeConnectorUtils;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.agenda.util.AgendaDateUtils;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

public class ExchangeConnectorStorage {

  private SettingService      settingService;

  public ExchangeConnectorStorage(SettingService settingService) {
    this.settingService = settingService;
  }

  public void createExchangeSetting(ExchangeUserSetting exchangeUserSetting, long userIdentityId) {

    String encodedPassword = ExchangeConnectorUtils.encode(exchangeUserSetting.getPassword());

    this.settingService.set(Context.USER.id(String.valueOf(userIdentityId)),
                            ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
                            ExchangeConnectorUtils.EXCHANGE_DOMAIN_NAME_KEY,
                            SettingValue.create(exchangeUserSetting.getDomainName()));
    this.settingService.set(Context.USER.id(String.valueOf(userIdentityId)),
                            ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
                            ExchangeConnectorUtils.EXCHANGE_USERNAME_KEY,
                            SettingValue.create(exchangeUserSetting.getUsername()));
    this.settingService.set(Context.USER.id(String.valueOf(userIdentityId)),
                            ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
                            ExchangeConnectorUtils.EXCHANGE_PASSWORD_KEY,
                            SettingValue.create(encodedPassword));
  }

  public ExchangeUserSetting getExchangeSetting(long userIdentityId) {

    SettingValue<?> domainName = this.settingService.get(Context.USER.id(String.valueOf(userIdentityId)),
            ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
            ExchangeConnectorUtils.EXCHANGE_DOMAIN_NAME_KEY);
    SettingValue<?> username = this.settingService.get(Context.USER.id(String.valueOf(userIdentityId)),
            ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
            ExchangeConnectorUtils.EXCHANGE_USERNAME_KEY);
    SettingValue<?> password = this.settingService.get(Context.USER.id(String.valueOf(userIdentityId)),
            ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
            ExchangeConnectorUtils.EXCHANGE_PASSWORD_KEY);

    ExchangeUserSetting exchangeUserSetting = new ExchangeUserSetting();
    if (domainName != null) {
      exchangeUserSetting.setDomainName((String) domainName.getValue());
    }
    if (username != null) {
      exchangeUserSetting.setUsername((String) username.getValue());
    }
    if (username != null) {
      String decodePassword = ExchangeConnectorUtils.decode((String) password.getValue());
      exchangeUserSetting.setPassword(decodePassword);
    }
    return exchangeUserSetting;
  }
  
  public void deleteExchangeSetting(long userIdentityId) {

    this.settingService.remove(Context.USER.id(String.valueOf(userIdentityId)),
            ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
            ExchangeConnectorUtils.EXCHANGE_DOMAIN_NAME_KEY);
    this.settingService.remove(Context.USER.id(String.valueOf(userIdentityId)),
            ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
            ExchangeConnectorUtils.EXCHANGE_USERNAME_KEY);
    this.settingService.remove(Context.USER.id(String.valueOf(userIdentityId)),
                               ExchangeConnectorUtils.EXCHANGE_CONNECTOR_SETTING_SCOPE,
                               ExchangeConnectorUtils.EXCHANGE_PASSWORD_KEY);
  }

  public static List<EventEntity>  getEvents(ZoneId userTimeZone){
    ZonedDateTime startDate = ZonedDateTime.of(LocalDate.now(), LocalTime.of(10, 0), userTimeZone).withZoneSameInstant(ZoneId.systemDefault());
    ZonedDateTime endDate = startDate.plusHours(1);
    String startDateEvent = AgendaDateUtils.toRFC3339Date(startDate);
    String endDateEvent = AgendaDateUtils.toRFC3339Date(endDate);
    List<EventEntity> events = new ArrayList<>();
    EventEntity event11 = new EventEntity();
    long id1= 10;
    long id2= 11;
    event11.setId(id1);
    event11.setSummary("event11");
    event11.setStart(startDateEvent);
    event11.setEnd(endDateEvent);
    EventEntity event22 = new EventEntity();
    event22.setId(id2);
    event22.setSummary("event22");
    event22.setStart(startDateEvent);
    event22.setEnd(endDateEvent);
    events.add(event11);
    events.add(event22);
    return events;
  }
}
