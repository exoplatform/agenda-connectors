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
