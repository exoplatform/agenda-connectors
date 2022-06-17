package org.exoplatform.agendaconnector.rest;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.exoplatform.agendaconnector.model.ExchangeUserSetting;
import org.exoplatform.agendaconnector.service.ExchangeConnectorService;
import org.exoplatform.agendaconnector.utils.ExchangeConnectorUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.manager.IdentityManager;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

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
  public Response createExchangeSetting(@ApiParam(value = "Exchange user setting object to create", required = true)
  ExchangeUserSetting exchangeUserSetting) {
    if (exchangeUserSetting == null) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    long identityId = ExchangeConnectorUtils.getCurrentUserIdentityId(identityManager);
    try {
      exchangeConnectorService.createExchangeSetting(exchangeUserSetting, identityId);
      return Response.ok().build();
    } catch (Exception e) {
      LOG.error("Error when creating exchange user setting ", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

}
