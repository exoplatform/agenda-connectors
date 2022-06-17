package org.exoplatform.agendaconnector.service;


import org.exoplatform.agendaconnector.model.ExchangeSetting;
import org.exoplatform.agendaconnector.storage.ExchangeConnectorStorage;


public class ExchangeConnectorServiceImpl implements  ExchangeConnectorService{


    private ExchangeConnectorStorage exchangeConnectorStorage;

    public ExchangeConnectorServiceImpl(ExchangeConnectorStorage exchangeConnectorStorage) {
        this.exchangeConnectorStorage = exchangeConnectorStorage;
    }


    @Override
    public Boolean connect(ExchangeSetting exchangeSetting) {
        return true;
    }

    @Override
    public ExchangeSetting createExchangeSetting(ExchangeSetting exchangeSetting) {
        return exchangeConnectorStorage.createExchangeSetting(exchangeSetting);
    }
}
