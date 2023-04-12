/*
 * Copyright (C) 2022 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agendaconnector.utils;

import java.net.URI;
import java.time.ZonedDateTime;

import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.enumeration.search.LogicalOperator;
import microsoft.exchange.webservices.data.core.service.schema.AppointmentSchema;
import microsoft.exchange.webservices.data.search.ItemView;
import microsoft.exchange.webservices.data.search.filter.SearchFilter;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agendaconnector.model.ExchangeUserSetting;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.web.security.codec.CodecInitializer;
import org.exoplatform.web.security.security.TokenServiceInitializationException;

import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.credential.ExchangeCredentials;
import microsoft.exchange.webservices.data.credential.WebCredentials;

public class ExchangeConnectorUtils {

  public static final String EXCHANGE_CREDENTIAL_CHECKED = "ExchangeCredentialChecked";

  private static final Log LOG = ExoLogger.getLogger(ExchangeConnectorUtils.class);

  public static final Scope  EXCHANGE_CONNECTOR_SETTING_SCOPE = Scope.APPLICATION.id("ExchangeAgendaConnector");

  public static final String EXCHANGE_USERNAME_KEY            = "ExchangeUsername";

  public static final String EXCHANGE_PASSWORD_KEY            = "ExchangePassword";
  
  public static final String EXCHANGE_SERVER_URL_PROPERTY = "exo.exchange.server.url";
  
  public static final String EWS_URL = "/EWS/Exchange.asmx";
  
  public static final String EXCHANGE_APPOINTMENT_SCHEMA_START = "calendar:Start";
  
  public static final String EXCHANGE_APPOINTMENT_SCHEMA_END = "calendar:End";
  
  private ExchangeConnectorUtils() {
  }

  public static final String getCurrentUser() {
    return ConversationState.getCurrent().getIdentity().getUserId();
  }

  public static final long getCurrentUserIdentityId(IdentityManager identityManager) {
    String currentUser = getCurrentUser();
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, currentUser);
    return identity == null ? 0 : Long.parseLong(identity.getId());
  }

  public static String encode(String password) {
    try {
      CodecInitializer codecInitializer = CommonsUtils.getService(CodecInitializer.class);
      return codecInitializer.getCodec().encode(password);
    } catch (TokenServiceInitializationException e) {
      LOG.warn("Error when encoding password", e);
      return null;
    }
  }

  public static String decode(String password) {
    try {
      CodecInitializer codecInitializer = CommonsUtils.getService(CodecInitializer.class);
      return codecInitializer.getCodec().decode(password);
    } catch (TokenServiceInitializationException e) {
      LOG.warn("Error when decoding password", e);
      return null;
    }
  }
  
  public static ExchangeService connectExchangeServer(ExchangeUserSetting exchangeUserSetting) throws Exception {
    ExchangeService exchangeService = new ExchangeService(ExchangeVersion.Exchange2010_SP2);
    exchangeService.setTimeout(300000);
    String exchangeUsername = exchangeUserSetting.getUsername();
    String exchangePassword = exchangeUserSetting.getPassword();
    String exchangeServerURL = System.getProperty(ExchangeConnectorUtils.EXCHANGE_SERVER_URL_PROPERTY);
    ExchangeCredentials credentials = new WebCredentials(exchangeUsername, exchangePassword);
    exchangeService.setCredentials(credentials);
    exchangeService.setUrl(new URI(exchangeServerURL + ExchangeConnectorUtils.EWS_URL));
    if (!exchangeUserSetting.isCredentialChecked()) {
      checkConnection(exchangeService);
      exchangeUserSetting.setCredentialChecked(true);
    }
    return exchangeService;
  }

  private static void checkConnection(ExchangeService exchangeService) throws Exception {
    //this function will verify if settings entered by user are functionnal

    ItemView view = new ItemView(100);

    ZonedDateTime startZonedDateTime = ZonedDateTime.now();
    SearchFilter exchangeStartSearchFilter =
        new SearchFilter.IsGreaterThanOrEqualTo(AppointmentSchema.Start,
                                                AgendaDateUtils.toDate(startZonedDateTime));
    ZonedDateTime endZonedDatetime = ZonedDateTime.now().plusDays(1);
    SearchFilter exchangeEndSearchFilter = new SearchFilter.IsLessThanOrEqualTo(AppointmentSchema.End,
                                                                                AgendaDateUtils.toDate(endZonedDatetime));

    SearchFilter exchangeEventsSearchFilter = new SearchFilter.SearchFilterCollection(LogicalOperator.And,
                                                                                      exchangeStartSearchFilter,
                                                                                      exchangeEndSearchFilter);

    exchangeService.findItems(WellKnownFolderName.Calendar,
                              exchangeEventsSearchFilter,
                              view);



  }
}
