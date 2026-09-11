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
package org.exoplatform.agendaconnector.rest;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.agenda.model.RemoteProvider;
import org.exoplatform.agenda.service.AgendaRemoteEventService;
import org.exoplatform.agendaconnector.service.GoogleConnectorService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.exoplatform.services.security.ConversationState;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

@Path("/v1/gconnector")
@Tag(name = "/gconnector", description = "Manages Google connector tokens and authorization")
public class GoogleConnectorRest implements ResourceContainer {

  private static final Log               LOG = ExoLogger.getLogger(GoogleConnectorRest.class);

  private final AgendaRemoteEventService agendaRemoteEventService;

  private final GoogleConnectorService   googleConnectorService;

  public GoogleConnectorRest(AgendaRemoteEventService agendaRemoteEventService, GoogleConnectorService googleConnectorService) {
    this.agendaRemoteEventService = agendaRemoteEventService;
    this.googleConnectorService = googleConnectorService;
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/oauth2callback")
  @Operation(summary = "Exchange an authorization code by an access and refresh token",
             description = "Exchange an authorization code by an access and refresh token", method = "POST")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response requestToken(@Parameter(description = "authorization code") @FormParam("code") String code,
                           @Parameter(description = "grant type") @FormParam("grantType") String grantType,
                           @Parameter(description = "redirect uri") @FormParam("redirectUri") String redirectUri,
                           @Parameter(description = "grant scopes") @FormParam("scopes") String scopes) {
    if (StringUtils.isBlank(code)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("authorization code is mandatory").build();
    }
    if (StringUtils.isBlank(grantType)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("grantType is mandatory").build();
    }
    String userName = ConversationState.getCurrent().getIdentity().getUserId();
    Optional<RemoteProvider> provider = getGoogleRemoteProvider();
    if (provider.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    try {
      RemoteProvider googleRemoteProvider = provider.get();
      GoogleAuthorizationCodeFlow.Builder flow = new GoogleAuthorizationCodeFlow.Builder(new NetHttpTransport(),
                                                                                         new GsonFactory(),
                                                                                         googleRemoteProvider.getApiKey(),
                                                                                         googleRemoteProvider.getSecretKey(),
                                                                                         Collections.singleton(scopes));
      flow.setAccessType("offline");
      GoogleAuthorizationCodeFlow authorizationCodeFlow = flow.build();
      GoogleTokenResponse response = authorizationCodeFlow.newTokenRequest(code)
                                                          .setGrantType(grantType)
                                                          .setRedirectUri(redirectUri)
                                                          .execute();
      googleConnectorService.saveTokenResponse(userName, response.toString());
      return Response.ok(response).build();
    } catch (Exception e) {
      LOG.error("Error while requesting refresh and access tokens", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/refreshaccess")
  @Operation(summary = "Refreshes an existing access token using a stored refresh token",
          description = "Refreshes an existing access token using a stored refresh token", method = "POST")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "400", description = "Invalid query input"),
          @ApiResponse(responseCode = "404", description = "Not found"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response refreshToken(@Parameter(description = "grant type") @FormParam("grantType") String grantType) {

    if (StringUtils.isBlank(grantType)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("grantType is mandatory").build();
    }
    String userName = ConversationState.getCurrent().getIdentity().getUserId();
    Optional<RemoteProvider> provider = getGoogleRemoteProvider();
    if (provider.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    try {
      RemoteProvider googleRemoteProvider = provider.get();
      String tokenResponse = googleConnectorService.getTokenResponse(userName);
      JSONParser parser = new JSONParser();
      Map<?, ?> responseMap = (Map<?, ?>) parser.parse(tokenResponse);
      String refreshToken = (String) responseMap.get("refresh_token");
      GoogleTokenResponse response = new GoogleRefreshTokenRequest(new NetHttpTransport(),
                                                                   new GsonFactory(),
                                                                   refreshToken,
                                                                   googleRemoteProvider.getApiKey(),
                                                                   googleRemoteProvider.getSecretKey()).execute();
      response.set("refresh_token", refreshToken);
      saveRefreshedToken(userName, response, responseMap);
      return Response.ok(response).build();
    } catch (Exception e) {
      LOG.error("Error while refreshing the access tokens", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Stores a refreshed token, keeping the scopes the previous one declared.
   * <p>
   * Merging and saving are one step on purpose: what has to be true of the
   * stored blob is that it always declares its scopes, and a carry-forward
   * that some future caller could save around would not deliver that.
   *
   * @param userName the user whose token is being replaced
   * @param response the freshly refreshed token, modified in place
   * @param storedTokenResponse the previously stored token as parsed JSON
   */
  void saveRefreshedToken(String userName, GoogleTokenResponse response, Map<?, ?> storedTokenResponse) {
    carryForwardScope(response, storedTokenResponse);
    googleConnectorService.saveTokenResponse(userName, response.toString());
  }

  /**
   * Keeps the granted scopes on a refreshed token when the refresh response
   * does not restate them.
   * <p>
   * The stored blob is replaced wholesale by the refresh response, and
   * RFC 6749 §5.1 makes <code>scope</code> OPTIONAL on that response
   * precisely when it is identical to what was granted. Losing it would make
   * the client read the account as holding no permissions at all, which is
   * how a capability derived from the grant gets switched off for good. This
   * is the same carry-forward the line above already performs for the refresh
   * token, and for the same reason.
   *
   * @param response the freshly refreshed token, modified in place
   * @param storedTokenResponse the previously stored token as parsed JSON
   */
  static void carryForwardScope(GoogleTokenResponse response, Map<?, ?> storedTokenResponse) {
    if (response == null || !StringUtils.isBlank(response.getScope())) {
      return;
    }
    Object storedScope = storedTokenResponse == null ? null : storedTokenResponse.get("scope");
    if (storedScope instanceof String scope && !StringUtils.isBlank(scope)) {
      response.setScope(scope);
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/token")
  @Operation(summary = "Get stored token response", description = "Get stored token response", method = "GET")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response getStoredToken() {
    String userName = ConversationState.getCurrent().getIdentity().getUserId();
    try {
      String tokenResponse = googleConnectorService.getTokenResponse(userName);
      if (tokenResponse == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
      }
      JSONParser parser = new JSONParser();
      JSONObject response = (JSONObject) parser.parse(tokenResponse);
      return Response.ok(response).build();
    } catch (Exception e) {
      LOG.error("Error while getting sored token", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @DELETE
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @Path("/token")
  @Operation(summary = "Remove stored token response", description = "Remove stored token response", method = "DELETE")
  @ApiResponses(value = {
          @ApiResponse(responseCode = "200", description = "Request fulfilled"),
          @ApiResponse(responseCode = "500", description = "Internal server error"), })
  public Response removeStoredToken() {
    String userName = ConversationState.getCurrent().getIdentity().getUserId();
    try {
      googleConnectorService.removeTokenResponse(userName);
      return Response.ok().build();
    } catch (Exception e) {
      LOG.error("Error while getting stored token", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  private Optional<RemoteProvider> getGoogleRemoteProvider() {
    return agendaRemoteEventService.getRemoteProviders()
                                   .stream()
                                   .filter(remoteProvider -> remoteProvider.getName().equals("agenda.googleCalendar"))
                                   .findFirst();
  }
}
