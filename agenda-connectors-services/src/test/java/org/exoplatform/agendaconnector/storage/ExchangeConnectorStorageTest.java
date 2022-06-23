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
    //Given
    long userIdentityId = 1;
    ExchangeUserSetting createdExchangeUserSetting = createExchangeSetting();
    
    //When
    exchangeConnectorStorage.createExchangeSetting(createdExchangeUserSetting, userIdentityId);
    
    //Then
    ExchangeUserSetting retrievedExchangeUserSetting = exchangeConnectorStorage.getExchangeSetting(userIdentityId);
    assertNotNull(retrievedExchangeUserSetting);
    assertEquals("exoplatform", retrievedExchangeUserSetting.getDomainName());
    assertEquals("Root", retrievedExchangeUserSetting.getUsername());
    assertEquals("Root123", retrievedExchangeUserSetting.getPassword());
  }
  
  @Test
  public void testDeleteExchangeSetting() {
    //Given
    long userIdentityId = 2;
    ExchangeUserSetting createdExchangeUserSetting = createExchangeSetting();
    exchangeConnectorStorage.createExchangeSetting(createdExchangeUserSetting, userIdentityId);
    
    //Then
    ExchangeUserSetting retrievedExchangeUserSetting = exchangeConnectorStorage.getExchangeSetting(userIdentityId);
    assertNotNull(retrievedExchangeUserSetting);
    assertNotNull(retrievedExchangeUserSetting.getDomainName());
    
    //When
    exchangeConnectorStorage.deleteExchangeSetting(userIdentityId);
    
    //Then
    retrievedExchangeUserSetting = exchangeConnectorStorage.getExchangeSetting(userIdentityId);
    assertNotNull(retrievedExchangeUserSetting);
    assertNull(retrievedExchangeUserSetting.getDomainName());
    assertNull(retrievedExchangeUserSetting.getUsername());
    assertNull(retrievedExchangeUserSetting.getPassword());
  }
  
  private ExchangeUserSetting createExchangeSetting() {
    ExchangeUserSetting createdExchangeUserSetting = new ExchangeUserSetting();
    createdExchangeUserSetting.setDomainName("exoplatform");
    createdExchangeUserSetting.setUsername("Root");
    createdExchangeUserSetting.setPassword("Root123");
    return createdExchangeUserSetting;
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
