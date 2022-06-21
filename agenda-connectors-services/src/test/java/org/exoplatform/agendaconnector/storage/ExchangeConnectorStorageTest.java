package org.exoplatform.agendaconnector.storage;

import org.exoplatform.agendaconnector.model.ExchangeUserSetting;
import org.exoplatform.agendaconnector.utils.ExchangeConnectorUtils;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.cache.concurrent.ConcurrentFIFOExoCache;
import org.exoplatform.services.listener.ListenerService;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

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
