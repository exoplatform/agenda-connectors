package org.exoplatform.agendaconnector.service;

import org.exoplatform.agendaconnector.model.ExchangeSetting;


public interface ExchangeConnectorService {


    /**
     * Connect to exchane setting
     *
     * @param exchangeSetting {@link ExchangeSetting} object to connect
     * @return connected boolean
     */
    Boolean connect(ExchangeSetting exchangeSetting);

    /**
     * Creates a new mail integration setting
     *
     * @param exchangeSetting {@link ExchangeSetting} object to create
     * @return created {@link ExchangeSetting} with generated technical identifier
     */
    void createExchangeSetting(ExchangeSetting exchangeSetting,long userIdentityId);
}
