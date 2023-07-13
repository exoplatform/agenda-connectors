/*
 * Copyright (C) 2023 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
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
