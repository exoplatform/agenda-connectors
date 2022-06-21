package org.exoplatform.agendaconnector.service;

import org.exoplatform.agendaconnector.model.ExchangeSetting;
import org.exoplatform.agendaconnector.utils.ExchangeConnectorUtils;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;

public class ExchangeConnectorServiceImpl implements ExchangeConnectorService {

  private SettingService      settingService;

  private static final Scope  EXCHANGE_CONNECTOR_SETTING_SCOPE = Scope.APPLICATION.id("ExchangeAgendaConnector");

  private static final String DOMAIN_NAME_KEY                  = "Exchange_DomainName";

  private static final String USERNAME_KEY                     = "Exchange_Username";

  private static final String PASSWORD_KEY                     = "Exchange_Password";

  public ExchangeConnectorServiceImpl(SettingService settingService) {
    this.settingService = settingService;
  }

  @Override
  public Boolean connect(ExchangeSetting exchangeSetting) {
    return true;
  }

  @Override
  public void createExchangeSetting(ExchangeSetting exchangeSetting, long userIdentityId) {
    if (userIdentityId <= 0) {
      throw new IllegalArgumentException("User identity id is mandatory");
    }
    if (exchangeSetting == null) {
      throw new IllegalArgumentException("Agenda settings are empty");
    }
    String encodedPassword = ExchangeConnectorUtils.encode(exchangeSetting.getPassword());

    this.settingService.set(Context.USER.id(String.valueOf(userIdentityId)),
                            EXCHANGE_CONNECTOR_SETTING_SCOPE,
                            DOMAIN_NAME_KEY,
                            SettingValue.create(exchangeSetting.getDomainName()));
    this.settingService.set(Context.USER.id(String.valueOf(userIdentityId)),
                            EXCHANGE_CONNECTOR_SETTING_SCOPE,
                            USERNAME_KEY,
                            SettingValue.create(exchangeSetting.getUsername()));
    this.settingService.set(Context.USER.id(String.valueOf(userIdentityId)),
                            EXCHANGE_CONNECTOR_SETTING_SCOPE,
                            PASSWORD_KEY,
                            SettingValue.create(encodedPassword));
  }
}
