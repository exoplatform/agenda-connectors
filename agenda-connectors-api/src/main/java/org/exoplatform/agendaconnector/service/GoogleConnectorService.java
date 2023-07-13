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

public interface GoogleConnectorService {

  /**
   * Saves google connector token response
   *
   * @param userName current user name
   * @param token token string
   */
  void saveTokenResponse(String userName, String token);

  /**
   * Get the stored google connector token response
   *
   * @param userName current username
   * @return token string
   */
  String getTokenResponse(String userName);

  /**
   * Remove stored google token response
   * @param userName current username
   */
  void removeTokenResponse(String userName);

}
