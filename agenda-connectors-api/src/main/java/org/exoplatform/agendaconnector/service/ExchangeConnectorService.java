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
}
