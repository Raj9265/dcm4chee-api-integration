package org.dcm4chee.arc.qido;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.dcm4chee.arc.query.QueryService;

import java.util.List;
@Path("/aets/{AETitle}/rs/series/sending-aets")
@RequestScoped
public class SendingAETRS {

    @Inject
    private QueryService service;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listDistinctSendingAETs(@PathParam("AETitle") String aet) {
        try {
            // we don't actually use AETitle in query here but it's in the path
            List<String> aets = service.listDistinctSendingAETs();
            return Response.ok(aets).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error retrieving sending AETs: " + e.getMessage())
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }
    }
}
