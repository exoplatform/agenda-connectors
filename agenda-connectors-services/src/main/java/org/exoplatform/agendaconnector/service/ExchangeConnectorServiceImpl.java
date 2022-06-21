package org.exoplatform.agendaconnector.service;

import org.exoplatform.agendaconnector.model.ExchangeUserSetting;
import org.exoplatform.agendaconnector.storage.ExchangeConnectorStorage;
import org.exoplatform.agendaconnector.utils.ExchangeConnectorUtils;


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
      throw new IllegalArgumentException("Agenda settings are empty");
    }
    String encodedPassword = ExchangeConnectorUtils.encode(exchangeUserSetting.getPassword());
    exchangeUserSetting.setPassword(encodedPassword);
    exchangeConnectorStorage.createExchangeSetting(exchangeUserSetting,userIdentityId);

  }
}
