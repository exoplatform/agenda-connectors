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

  public static List<EventEntity> getEvents(String start, String end,ZoneId userTimeZone){

    ZonedDateTime startDate1 = ZonedDateTime.of(LocalDate.now(), LocalTime.of(10, 0), userTimeZone).withZoneSameInstant(ZoneId.systemDefault());
    ZonedDateTime startDate2 = startDate1.plusDays(1);
    ZonedDateTime startDate3 = startDate2.plusDays(7);
    ZonedDateTime startDate4 = startDate3.plusDays(1);



    String startDateEvent1 = AgendaDateUtils.toRFC3339Date(startDate1);
    String startDateEvent2 = AgendaDateUtils.toRFC3339Date(startDate2);
    String startDateEvent3 = AgendaDateUtils.toRFC3339Date(startDate3);
    String startDateEvent4 = AgendaDateUtils.toRFC3339Date(startDate4);

    String endDateEvent1 = AgendaDateUtils.toRFC3339Date(startDate1.plusHours(1));
    String endDateEvent2 = AgendaDateUtils.toRFC3339Date(startDate2.plusHours(1));
    String endDateEvent3 = AgendaDateUtils.toRFC3339Date(startDate3.plusHours(1));
    String endDateEvent4 = AgendaDateUtils.toRFC3339Date(startDate4.plusHours(1));

    List<EventEntity> events = new ArrayList<>();
    EventEntity event1 = new EventEntity();

    event1.setId(10);
    event1.setSummary("event1");
    event1.setStart(startDateEvent1);
    event1.setEnd(endDateEvent1);

    EventEntity event2 = new EventEntity();
    event2.setId(11);
    event2.setSummary("event2");
    event2.setStart(startDateEvent2);
    event2.setEnd(endDateEvent2);

    EventEntity event3 = new EventEntity();
    event3.setId(12);
    event3.setSummary("event3");
    event3.setStart(startDateEvent3);
    event3.setEnd(endDateEvent3);

    EventEntity event4 = new EventEntity();
    event4.setId(13);
    event4.setSummary("event4");
    event4.setStart(startDateEvent4);
    event4.setEnd(endDateEvent4);

    events.add(event1);
    events.add(event2);
    events.add(event3);
    events.add(event4);

    return events;
  }
}
