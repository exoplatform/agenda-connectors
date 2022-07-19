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
package org.exoplatform.agendaconnector.rest;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agendaconnector.model.ExchangeUserSetting;
import org.exoplatform.agendaconnector.service.ExchangeConnectorService;
import org.exoplatform.agendaconnector.utils.ExchangeConnectorUtils;
import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Path("/v1/exchange")
public class ExchangeConnectorRest implements ResourceContainer {

  private static final Log         LOG = ExoLogger.getLogger(ExchangeConnectorRest.class);

  private ExchangeConnectorService exchangeConnectorService;

  private IdentityManager          identityManager;

  public ExchangeConnectorRest(ExchangeConnectorService exchangeConnectorService, IdentityManager identityManager) {
    this.exchangeConnectorService = exchangeConnectorService;
    this.identityManager = identityManager;
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Create exchange user setting", httpMethod = "POST", response = Response.class, consumes = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error") })
  public Response createExchangeSetting(@ApiParam(value = "Exchange user setting object to create", required = true) ExchangeUserSetting exchangeUserSetting) {
    if (exchangeUserSetting == null) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    long identityId = ExchangeConnectorUtils.getCurrentUserIdentityId(identityManager);
    try {
      exchangeConnectorService.createExchangeSetting(exchangeUserSetting, identityId);
      return Response.ok().build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' is not autorized to connect to exchange server", identityId, e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.error("Error when creating exchange user setting ", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @GET
  @RolesAllowed("users")
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Get exchange user setting", httpMethod = "GET", response = Response.class, produces = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error") })
  public Response getExchangeSetting() {
    long identityId = ExchangeConnectorUtils.getCurrentUserIdentityId(identityManager);
    try {
      return Response.ok(exchangeConnectorService. getExchangeSetting(identityId)).build();
    } catch (Exception e) {
      LOG.error("Error when retrieving exchange user settings for user with id '{}'", identityId, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @DELETE
  @RolesAllowed("users")
  @ApiOperation(value = "Delete exchange user setting", httpMethod = "DELETE", response = Response.class)
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error") })
  public Response deleteExchangeSetting() {
    long identityId = ExchangeConnectorUtils.getCurrentUserIdentityId(identityManager);
    try {
      exchangeConnectorService.deleteExchangeSetting(identityId);
      return Response.ok().build();
    } catch (Exception e) {
      LOG.error("Error when deleting exchange user setting for user with id '{}'", identityId, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @GET
  @Path("/events")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Retrieve the remote exchange events from exchange agenda", httpMethod = "GET", response = Response.class, produces = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error") })
  public Response getExchangeEvents(
                            @ApiParam(value = "Start datetime using RFC-3339 representation", required = true)
                            @QueryParam("start")
                            String start,
                            @ApiParam(value = "End datetime using RFC-3339 representation", required = false)
                            @QueryParam("end")
                            String end,
                            @ApiParam(value = "IANA Time zone identitifer", required = false)
                            @QueryParam("timeZoneId")
                            String timeZoneId) {

    long identityId = ExchangeConnectorUtils.getCurrentUserIdentityId(identityManager);
    if (StringUtils.isBlank(start)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Start datetime is mandatory").build();
    }
    if (StringUtils.isBlank(end)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("End datetime is mandatory").build();
    }
    if (StringUtils.isBlank(timeZoneId)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Time zone is mandatory").build();
    }
    ZoneId userTimeZone = StringUtils.isBlank(timeZoneId) ? ZoneOffset.UTC : ZoneId.of(timeZoneId);
    try {
      List<EventEntity> events = exchangeConnectorService.getExchangeEvents(identityId, start, end, userTimeZone);
      return Response.ok(events).build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' is not autorized to connect to exchange server or get exchange event informations", identityId, e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.error("Error when retrieving user exchange events ", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @POST
  @Path("/event/push")
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Push event to exchange agenda", httpMethod = "POST", response = Response.class, consumes = "application/json")
  @ApiResponses(value = { @ApiResponse(code = HTTPStatus.OK, message = "Request fulfilled"),
      @ApiResponse(code = HTTPStatus.BAD_REQUEST, message = "Invalid query input"),
      @ApiResponse(code = HTTPStatus.UNAUTHORIZED, message = "Unauthorized operation"),
      @ApiResponse(code = HTTPStatus.INTERNAL_ERROR, message = "Internal server error") })
  public Response pushEventToExchange(@ApiParam(value = "Event object", required = true)
                                        EventEntity event,
                                      @ApiParam(value = "IANA Time zone identitifer", required = false)
                                      @QueryParam("timeZoneId")
                                      String timeZoneId) {
    if (event == null) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    long identityId = ExchangeConnectorUtils.getCurrentUserIdentityId(identityManager);
    if (StringUtils.isBlank(timeZoneId)) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Time zone is mandatory").build();
    }
    ZoneId userTimeZone = StringUtils.isBlank(timeZoneId) ? ZoneOffset.UTC : ZoneId.of(timeZoneId);
    try {
      exchangeConnectorService.pushEventToExchange(identityId, event, userTimeZone);
      return Response.ok().build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' is not autorized to connect to exchange server or push exchange event informations", identityId, e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.error("Error when pushing event in exchange agenda ", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @DELETE
  @Path("{eventId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("users")
  @ApiOperation(value = "Delete an exchange event from exchange agenda", httpMethod = "DELETE", response = Response.class)
  public Response deleteExchangeEvent(
                                      @ApiParam(value = "Event technical identifier", required = true)
                                      @PathParam("eventId")
                                      long eventId) {
    if (eventId <= 0) {
      return Response.status(Response.Status.BAD_REQUEST).entity("Event technical identifier must be positive").build();
    }
    long identityId = ExchangeConnectorUtils.getCurrentUserIdentityId(identityManager);
    try {
      exchangeConnectorService.deleteExchangeEvent(identityId, eventId);
      return Response.ok().build();
    } catch (IllegalAccessException e) {
      LOG.warn("User '{}' is not autorized to connect to exchange server or remove exchange event", identityId, e);
      return Response.status(Response.Status.UNAUTHORIZED).entity(e.getMessage()).build();
    } catch (Exception e) {
      LOG.error("Error when removing exchange event from exchange agenda ", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }
}