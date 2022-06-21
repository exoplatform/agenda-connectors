package org.exoplatform.agendaconnector.service;

import org.exoplatform.agendaconnector.model.ExchangeUserSetting;

public interface ExchangeConnectorService {

  /**
   * Creates a new exchange user setting
   *
   * @param exchangeUserSetting {@link ExchangeUserSetting} object to create
   */
  void createExchangeSetting(ExchangeUserSetting exchangeUserSetting, long userIdentityId);



  ExchangeUserSetting getExchangeSetting(long userIdentityId);
}
