/*
 * *** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2017-2019
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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.export.rs;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.conf.QueueDescriptor;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.qmgt.impl.TaskScheduler;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.scu.CStoreSCU;
import org.dcm4chee.arc.stow.client.StowClient;
import org.dcm4chee.arc.validation.ParseDateTime;
import org.dcm4chee.arc.validation.constraints.ValidValueOf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2016
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class ExporterRS {

    private static final Logger LOG = LoggerFactory.getLogger(ExporterRS.class);
    private static final String SUPER_USER_ROLE = "super-user-role";

    @Inject
    private Device device;

    @Inject
    private RetrieveService retrieveService;

    @Inject
    private CStoreSCU storeSCU;

    @Inject
    private StowClient stowClient;

    @Inject
    private ExportManager exportManager;

    @Inject
    private TaskScheduler taskScheduler;

    @PathParam("AETitle")
    private String aet;

    @QueryParam("batchID")
    private String batchID;

    @QueryParam("scheduledTime")
    @ValidValueOf(type = ParseDateTime.class)
    private String scheduledTime;

    @Context
    private HttpServletRequest request;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    @POST
    @Path("/studies/{StudyUID}/export/{ExporterID}")
    @Produces("application/json")
    public Response exportStudy(
            @PathParam("StudyUID") String studyUID,
            @PathParam("ExporterID") String exporterID) {
        return export(studyUID, null, null, exporterID);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/export/{ExporterID}")
    @Produces("application/json")
    public Response exportSeries(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("ExporterID") String exporterID) {
        return export(studyUID, seriesUID, null, exporterID);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/instances/{ObjectUID}/export/{ExporterID}")
    @Produces("application/json")
    public Response exportInstance(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("ObjectUID") String objectUID,
            @PathParam("ExporterID") String exporterID) {
        return export(studyUID, seriesUID, objectUID, exporterID);
    }

    private Response export(String studyUID, String seriesUID, String objectUID, String exporterID) {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcDev == null)
            return errResponse("Archive Device Extension not configured for device: " + device.getDeviceName(),
                    Response.Status.NOT_FOUND);

        try {
            try {
                if (exporterID.startsWith("dicom:"))
                    return dicomExport(studyUID, seriesUID, objectUID, exporterID.substring(6));

                if (exporterID.startsWith("stowrs:"))
                    return stowExport(studyUID, seriesUID, objectUID, exporterID.substring(7));

            } catch (DicomServiceException e) {
                return errResponse(e.getMessage(), Response.Status.BAD_GATEWAY);
            } catch (ConfigurationNotFoundException e) {
                return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
            }

            ExporterDescriptor exporter = arcDev.getExporterDescriptor(exporterID);
            if (exporter == null) {
                return errResponse("No such Exporter: " + exporterID, Response.Status.NOT_FOUND);
            }
            Task exportTask = exportManager.createExportTask(
                    device.getDeviceName(),
                    exporter,
                    studyUID,
                    seriesUID == null ? "*" : seriesUID,
                    objectUID == null ? "*" : objectUID,
                    batchID,
                    scheduledTime(),
                    HttpServletRequestInfo.valueOf(request));
            if (scheduledTime == null) {
                QueueDescriptor queue = arcDev.getQueueDescriptor(exporter.getQueueName());
                if (queue == null)
                    return errResponse("No queue configured for Exporter: " + exporterID, Response.Status.NOT_FOUND);
                taskScheduler.process(queue, arcDev.getTaskFetchSize());
            }
            return Response.accepted().entity(writeJSON(exportTask)).build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private StreamingOutput writeJSON(Task exportTask) {
        return out -> {
            Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            try (JsonGenerator gen = Json.createGenerator(w)) {
                exportTask.writeAsJSON(gen);
            }
        };
    }

    private Date scheduledTime() {
        if (scheduledTime != null)
            try {
                return new SimpleDateFormat("yyyyMMddhhmmss").parse(scheduledTime);
            } catch (Exception e) {
                LOG.info(e.getMessage());
            }

        return new Date();
    }

    private Response dicomExport(String studyUID, String seriesUID, String objectUID, String destAET)
            throws Exception{
        RetrieveContext retrieveContext = retrieveService.newRetrieveContextSTORE(
                aet, studyUID, seriesUID, objectUID, destAET);
        retrieveContext.setHttpServletRequestInfo(HttpServletRequestInfo.valueOf(request));
        if (retrieveService.calculateMatches(retrieveContext)
                && retrieveService.restrictRetrieveAccordingTransferCapabilities(retrieveContext))
            storeSCU.newRetrieveTaskSTORE(retrieveContext).run();
        return toResponse(retrieveContext);
    }

    private Response stowExport(String studyUID, String seriesUID, String objectUID, String destWebApp)
            throws Exception{
        RetrieveContext retrieveContext = retrieveService.newRetrieveContextSTOW(
                aet, studyUID, seriesUID, objectUID, destWebApp);
        retrieveContext.setHttpServletRequestInfo(HttpServletRequestInfo.valueOf(request));
        if (retrieveService.calculateMatches(retrieveContext))
            stowClient.newStowTask(retrieveContext).run();
        return toResponse(retrieveContext);
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private static Response toResponse(RetrieveContext retrieveContext) {
        return Response.status(status(retrieveContext)).entity(entity(retrieveContext)).build();
    }

    private static Response.Status status(RetrieveContext ctx) {
        return ctx.getException() != null
                ? Response.Status.BAD_GATEWAY
                : ctx.failed() == 0
                    ? Response.Status.OK
                    : ctx.completed() + ctx.warning() > 0
                        ? Response.Status.PARTIAL_CONTENT
                        : Response.Status.BAD_GATEWAY;
    }

    private static Object entity(final RetrieveContext ctx) {
        return (StreamingOutput) out -> {
                JsonGenerator gen = Json.createGenerator(out);
                JsonWriter writer = new JsonWriter(gen);
                gen.writeStartObject();
                gen.write("completed", ctx.completed());
                writer.writeNotDef("warning", ctx.warning(), 0);
                writer.writeNotDef("failed", ctx.failed(), 0);
                writer.writeNotNullOrDef("error", ctx.getException(), null);
                gen.writeEnd();
                gen.flush();
        };
    }

    private Response errResponse(String msg, Response.Status status) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + msg + "\"}", status);
    }

    private Response errResponseAsTextPlain(String errorMsg, Response.Status status) {
        LOG.warn("Response {} caused by {}", status, errorMsg);
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

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae == null || !ae.isInstalled() ? null : ae.getAEExtension(ArchiveAEExtension.class);
    }

    private void validateAcceptedUserRoles(ArchiveAEExtension arcAE) {
        KeycloakContext keycloakContext = KeycloakContext.valueOf(request);
        if (keycloakContext.isSecured() && !keycloakContext.isUserInRole(System.getProperty(SUPER_USER_ROLE))) {
            if (!arcAE.isAcceptedUserRole(keycloakContext.getRoles()))
                throw new WebApplicationException(
                        "Application Entity " + arcAE.getApplicationEntity().getAETitle() + " does not list role of accessing user",
                        Response.Status.FORBIDDEN);
        }
    }

    private void validateWebAppServiceClass() {
        device.getWebApplications().stream()
                .filter(webApp -> request.getRequestURI().startsWith(webApp.getServicePath())
                        && Arrays.asList(webApp.getServiceClasses())
                        .contains(WebApplication.ServiceClass.DCM4CHEE_ARC_AET))
                .findFirst()
                .orElseThrow(() -> new WebApplicationException(errResponse(
                        "No Web Application with DCM4CHEE_ARC_AET service class found for Application Entity: " + aet,
                        Response.Status.NOT_FOUND)));
    }
}
