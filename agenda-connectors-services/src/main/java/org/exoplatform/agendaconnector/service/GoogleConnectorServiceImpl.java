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

public class GoogleConnectorServiceImpl implements GoogleConnectorService {

  private static final Scope   APP_SCOPE                     = Scope.APPLICATION.id("gConnectorRefreshToken");

  private static final String  G_CONNECTOR_REFRESH_TOKEN_KEY = "gConnectorRefreshTokenKey";

  private final SettingService settingService;

  public GoogleConnectorServiceImpl(SettingService settingService) {
    this.settingService = settingService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void saveTokenResponse(String userName, String token) {
    if (userName == null) {
      throw new IllegalArgumentException("username is mandatory");
    }
    if (token == null) {
      throw new IllegalArgumentException("token object is mandatory");
    }
    settingService.set(Context.USER.id(userName), APP_SCOPE, G_CONNECTOR_REFRESH_TOKEN_KEY, SettingValue.create(token));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getTokenResponse(String userName) {
    if (userName == null) {
      throw new IllegalArgumentException("username is mandatory");
    }
    SettingValue<?> tokenResponse = settingService.get(Context.USER.id(userName), APP_SCOPE, G_CONNECTOR_REFRESH_TOKEN_KEY);
    if (tokenResponse == null) {
      return null;
    }
    return (String) tokenResponse.getValue();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void removeTokenResponse(String userName) {
    if (userName == null) {
      throw new IllegalArgumentException("username is mandatory");
    }
    settingService.remove(Context.USER.id(userName), APP_SCOPE, G_CONNECTOR_REFRESH_TOKEN_KEY);
  }
}
