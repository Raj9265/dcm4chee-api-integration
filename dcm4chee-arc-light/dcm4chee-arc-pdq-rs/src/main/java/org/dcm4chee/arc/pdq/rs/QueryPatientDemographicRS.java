/*
 * **** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.pdq.rs;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.PDQServiceDescriptor;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.pdq.PDQServiceContext;
import org.dcm4chee.arc.pdq.PDQServiceException;
import org.dcm4chee.arc.pdq.PDQServiceFactory;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2018
 */
@RequestScoped
@Path("/pdq/{PDQServiceID}")
public class QueryPatientDemographicRS {
    private static final Logger LOG = LoggerFactory.getLogger(QueryPatientDemographicRS.class);

    @Context
    private HttpServletRequest request;

    @Inject
    private Device device;

    @Inject
    private PDQServiceFactory serviceFactory;

    @PathParam("PDQServiceID")
    private String pdqServiceID;

    @GET
    @NoCache
    @Path("/patients/{PatientID}")
    @Produces("application/dicom+json,application/json")
    public Response query(@PathParam("PatientID") String multiplePatientIDs) {
        logRequest();
        Attributes attrs;
        ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        try {
            PDQServiceDescriptor descriptor = arcdev.getPDQServiceDescriptor(pdqServiceID);
            if (descriptor == null)
                return errResponse("No such PDQ Service: " + pdqServiceID, Response.Status.NOT_FOUND);

            Collection<IDWithIssuer> trustedPatientIDs = trustedPatientIDs(multiplePatientIDs);
            if (trustedPatientIDs.isEmpty())
                return errResponse(
                        "Missing patient identifier with trusted assigning authority in " + multiplePatientIDs,
                        Response.Status.BAD_REQUEST);

            PDQServiceContext ctx = new PDQServiceContext(trustedPatientIDs.iterator().next());
            ctx.setHttpServletRequestInfo(HttpServletRequestInfo.valueOf(request));
            ctx.setSearchMethod(PDQServiceContext.SearchMethod.QueryPatientDemographics);
            attrs = serviceFactory.getPDQService(descriptor).query(ctx);
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (PDQServiceException e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.BAD_GATEWAY);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
        return attrs != null
                ? Response.ok(toJSON(attrs, arcdev)).build()
                : errResponse("Querying the PDQ Service returned null attributes", Response.Status.NOT_FOUND);
    }

    private Collection<IDWithIssuer> trustedPatientIDs(String multiplePatientIDs) {
        String[] patientIDs = multiplePatientIDs.split("~");
        Set<IDWithIssuer> patientIdentifiers = new LinkedHashSet<>(patientIDs.length);
        for (String cx : patientIDs)
            patientIdentifiers.add(new IDWithIssuer(cx));
        return device.getDeviceExtension(ArchiveDeviceExtension.class)
                .retainTrustedPatientIDs(patientIdentifiers);
    }

    private void logRequest() {
        LOG.info("Process GET {} from {}@{}",
                request.getRequestURI(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private Response errResponse(String msg, Response.Status status) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + msg + "\"}", status);
    }

    private Response errResponseAsTextPlain(String errorMsg, Response.Status status) {
        LOG.warn("Response {} caused by {} ", status, errorMsg);
        return Response.status(status)
                .entity(errorMsg)
                .type("text/plain")
                .build();
    }

    private String exceptionAsString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private StreamingOutput toJSON(Attributes attrs, ArchiveDeviceExtension arcDev) {
        return out -> {
            try (JsonGenerator gen = Json.createGenerator(out)) {
                (arcDev.encodeAsJSONNumber(new JSONWriter(gen))).write(attrs);
            }
        };
    }
}
