package org.exoplatform.agendaconnector.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import org.exoplatform.agendaconnector.model.ExchangeUserSetting;
import org.exoplatform.container.PortalContainer;

public class ExchangeConnectorStorageTest {

    private  ExchangeConnectorStorage exchangeConnectorStorage;

    @Before
    public void setUp() throws Exception { // NOSONAR
        PortalContainer container = PortalContainer.getInstance();
        exchangeConnectorStorage= container.getComponentInstanceOfType(ExchangeConnectorStorage.class);
    }

    @Test
    public void testCreateExchangeSetting() {

        long userIdentityId = 1;
        ExchangeUserSetting createdExchangeUserSetting = new ExchangeUserSetting();
        createdExchangeUserSetting.setDomainName("exoplatform");
        createdExchangeUserSetting.setUsername("Root");
        createdExchangeUserSetting.setPassword("Root123");
        exchangeConnectorStorage.createExchangeSetting(createdExchangeUserSetting, userIdentityId);
        ExchangeUserSetting retrievedExchangeUserSetting = exchangeConnectorStorage.getExchangeSetting(userIdentityId);
        assertNotNull(retrievedExchangeUserSetting);
        assertEquals("exoplatform", retrievedExchangeUserSetting.getDomainName());
        assertEquals("Root", retrievedExchangeUserSetting.getUsername());
        assertEquals("Root123", retrievedExchangeUserSetting.getPassword());

    }
}
