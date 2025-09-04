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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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

package org.dcm4chee.arc.xroad.rs;

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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.xml.ws.Holder;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.xroad.*;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2018
 */
@RequestScoped
@Path("/xroad")
public class XRoadRS {
    private static final Logger LOG = LoggerFactory.getLogger(XRoadRS.class);

    @Context
    private HttpServletRequest request;

    @Inject
    private Device device;

    @GET
    @NoCache
    @Path("/RR441/{PatientID}")
    @Produces("application/dicom+json,application/json")
    public Response rr441(@PathParam("PatientID") String multiplePatientIDs) throws Exception {
        logRequest();
        Map<String, String> props = device.getDeviceExtension(ArchiveDeviceExtension.class)
                .getXRoadProperties();
        String endpoint = props.get("endpoint");
        if (endpoint == null)
            throw new ConfigurationException("Missing XRoadProperty endpoint");

        Attributes attrs;
        try {
            Collection<IDWithIssuer> trustedPatientIDs = trustedPatientIDs(multiplePatientIDs);
            if (trustedPatientIDs.isEmpty())
                return errResponse(
                        "Missing patient identifier with trusted assigning authority in " + multiplePatientIDs,
                        Response.Status.BAD_REQUEST);

            XRoadAdapterPortType port = new XRoadService().getXRoadServicePort();
            XRoadUtils.setEndpointAddress(port, endpoint);
            if (endpoint.startsWith("https")) {
                XRoadUtils.setTlsClientParameters(port, device,
                        props.get("TLS.protocol"),
                        StringUtils.split(props.get("TLS.cipherSuites"), ','),
                        Boolean.parseBoolean(props.getOrDefault("TLS.disableCNCheck", "false")));
            }
            RR441RequestType rq = XRoadUtils.createRR441RequestType(props, trustedPatientIDs.iterator().next().getID());
            RR441ResponseType rsp = XRoadException.validate(XRoadUtils.rr441(port, props, rq, new Holder<>()));
            attrs = XRoadUtils.toAttributes(
                    props.getOrDefault("SpecificCharacterSet", "ISO_IR 100"),
                    rsp);
        } catch (XRoadException e) {
            return errResponse(e.getMessage(), Response.Status.BAD_GATEWAY);
        }
        return (attrs == null ? Response.status(Response.Status.NOT_FOUND) : Response.ok(toJSON(attrs))).build();
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
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }

    private Response errResponse(String errorMessage, Response.Status status) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity("{\"errorMessage\":\"" + errorMessage + "\"}")
                .build();
    }

    private StreamingOutput toJSON(Attributes attrs) {
        return out -> {
            try (JsonGenerator gen = Json.createGenerator(out)) {
                (device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                        .encodeAsJSONNumber(new JSONWriter(gen)))
                        .write(attrs);
            }
        };
    }
}
