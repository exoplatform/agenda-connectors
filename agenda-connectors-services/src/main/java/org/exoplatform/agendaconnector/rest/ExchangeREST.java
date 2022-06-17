package org.exoplatform.agendaconnector.rest;

import javax.ws.rs.Path;

import org.exoplatform.agendaconnector.model.ExchangeSetting;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import  org.exoplatform.agendaconnector.service.ExchangeConnectorService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

@Path("/exchange")
public class ExchangeREST implements ResourceContainer {

    private static final Log LOG = ExoLogger.getLogger(ExchangeREST.class);

    ExchangeConnectorService exchangeConnectorService;

    public ExchangeREST(ExchangeConnectorService exchangeConnectorService) {
        this.exchangeConnectorService = exchangeConnectorService;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create exchange setting", httpMethod = "POST", response = Response.class, consumes = "application/json")
    public Response createExchangeSetting(@ApiParam(value = "Exchange setting object to create", required = true)
                                                 ExchangeSetting exchangeSetting) {
        if (exchangeSetting == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        try {
            ExchangeSetting exchangeSetting1 = exchangeConnectorService.createExchangeSetting(exchangeSetting);
            return Response.ok(exchangeSetting1).build();
        } catch (Exception e) {
            LOG.error("Error when creating exchange setting ", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }
}
