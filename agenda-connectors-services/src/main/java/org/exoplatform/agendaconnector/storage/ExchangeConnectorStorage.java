package org.exoplatform.agendaconnector.storage;

import org.exoplatform.agendaconnector.model.ExchangeUserSetting;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;

public class ExchangeConnectorStorage {


  private static final Scope EXCHANGE_CONNECTOR_SETTING_SCOPE = Scope.APPLICATION.id("ExchangeAgendaConnector");

  private static final String DOMAIN_NAME_KEY                  = "Exchange_DomainName";

  private static final String USERNAME_KEY                     = "Exchange_Username";

  private static final String PASSWORD_KEY                     = "Exchange_Password";

  private SettingService settingService;

  public ExchangeConnectorStorage(SettingService settingService) {
    this.settingService = settingService;
  }

  public void createExchangeSetting(ExchangeUserSetting exchangeUserSetting,long userIdentityId) {

    this.settingService.set(Context.USER.id(String.valueOf(userIdentityId)),
            EXCHANGE_CONNECTOR_SETTING_SCOPE,
            DOMAIN_NAME_KEY,
            SettingValue.create(exchangeUserSetting.getDomainName()));
    this.settingService.set(Context.USER.id(String.valueOf(userIdentityId)),
            EXCHANGE_CONNECTOR_SETTING_SCOPE,
            USERNAME_KEY,
            SettingValue.create(exchangeUserSetting.getUsername()));
    this.settingService.set(Context.USER.id(String.valueOf(userIdentityId)),
            EXCHANGE_CONNECTOR_SETTING_SCOPE,
            PASSWORD_KEY,
            SettingValue.create(exchangeUserSetting.getPassword()));

  }
}
