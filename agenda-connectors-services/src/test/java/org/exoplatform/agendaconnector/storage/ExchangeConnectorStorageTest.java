package org.exoplatform.agendaconnector.storage;

import org.hibernate.ObjectNotFoundException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.exoplatform.agendaconnector.model.ExchangeUserSetting;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;

import junit.framework.TestCase;

public class ExchangeConnectorStorageTest extends TestCase {

  private ExchangeConnectorStorage exchangeConnectorStorage;

  private PortalContainer          container;

  @Before
  public void setUp() throws Exception {
    container = PortalContainer.getInstance();
    exchangeConnectorStorage = container.getComponentInstanceOfType(ExchangeConnectorStorage.class);
    begin();
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

  private void begin() {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
  }

  private void end() {
    RequestLifeCycle.end();
  }

  @After
  public void tearDown() throws ObjectNotFoundException {
    end();
  }
}
