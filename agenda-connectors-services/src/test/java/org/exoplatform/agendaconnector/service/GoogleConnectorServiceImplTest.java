package org.exoplatform.agendaconnector.service;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GoogleConnectorServiceImplTest {

  private SettingService         settingService;

  private GoogleConnectorService googleConnectorService;

  private static final Scope     APP_SCOPE                     = Scope.APPLICATION.id("gConnectorRefreshToken");

  private static final String    G_CONNECTOR_REFRESH_TOKEN_KEY = "gConnectorRefreshTokenKey";

  @Before
  public void setUp() throws Exception {
    settingService = mock(SettingService.class);
    googleConnectorService = new GoogleConnectorServiceImpl(settingService);
  }

  @Test
  public void saveTokenResponse() {
    Throwable exception =
                        assertThrows(IllegalArgumentException.class, () -> googleConnectorService.saveTokenResponse(null, null));
    assertEquals("username is mandatory", exception.getMessage());
    exception = assertThrows(IllegalArgumentException.class, () -> googleConnectorService.saveTokenResponse("user", null));
    assertEquals("token object is mandatory", exception.getMessage());
    googleConnectorService.saveTokenResponse("user", "token");
    verify(settingService, times(1)).set(any(Context.class), any(Scope.class), anyString(), any());

  }

  @Test
  public void getTokenResponse() {
    Throwable exception =
                        assertThrows(IllegalArgumentException.class, () -> googleConnectorService.saveTokenResponse(null, null));
    assertEquals("username is mandatory", exception.getMessage());
    googleConnectorService.getTokenResponse("user");
    verify(settingService, times(1)).get(any(Context.class), any(Scope.class), anyString());
  }

  @Test
  public void removeTokenResponse() {
    Throwable exception =
                        assertThrows(IllegalArgumentException.class, () -> googleConnectorService.saveTokenResponse(null, null));
    assertEquals("username is mandatory", exception.getMessage());
    googleConnectorService.removeTokenResponse("user");
    verify(settingService, times(1)).remove(any(Context.class), any(Scope.class), anyString());
  }
}
