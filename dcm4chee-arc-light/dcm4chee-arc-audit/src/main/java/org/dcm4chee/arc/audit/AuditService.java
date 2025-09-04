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

package org.dcm4chee.arc.audit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import org.dcm4che3.audit.*;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.audit.AuditLoggerDeviceExtension;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.AssociationEvent;
import org.dcm4chee.arc.ConnectionEvent;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.event.*;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.pdq.PDQServiceContext;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.qstar.QStarVerification;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.stgcmt.StgCmtContext;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2016
 */
@ApplicationScoped
public class AuditService {
    private final static Logger LOG = LoggerFactory.getLogger(AuditService.class);

    @Inject
    private Device device;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private IHL7ApplicationCache hl7AppCache;

    @Inject
    private IWebApplicationCache webAppCache;

    private void aggregateAuditMessage(AuditLogger auditLogger, Path path) throws Exception {
        AuditUtils.EventType eventType = AuditUtils.EventType.fromFile(path);
        if (path.toFile().length() == 0) {
            LOG.info("Attempt to read from an empty file {} by {}.", path, eventType);
            return;
        }
        switch (eventType.eventClass) {
            case APPLN_ACTIVITY:
                ApplicationActivityAuditService.audit(auditLogger, path, eventType);
                break;
            case CONN_FAILURE:
                ServiceEventsAuditService.auditConnectionEvent(auditLogger, path, eventType);
                break;
            case ASSOCIATION_FAILURE:
                ServiceEventsAuditService.auditAssociationEvent(auditLogger, path, eventType);
                break;
            case STORE_WADOR:
                if (eventType.name().startsWith("WADO")) RetrieveAuditService.auditWADOURI(auditLogger, path, eventType);
                else StoreAuditService.audit(auditLogger, path, eventType);
                break;
            case RETRIEVE:
                RetrieveAuditService.audit(auditLogger, path, eventType);
                break;
            case USER_DELETED:
            case SCHEDULER_DELETED:
                DeletionAuditService.audit(auditLogger, path, eventType);
                break;
            case QUERY:
                auditQuery(auditLogger, path, eventType);
                break;
            case PATIENT:
                PatientRecordAuditService.audit(auditLogger, path, eventType, device, aeCache, hl7AppCache, webAppCache);
                break;
            case PROCEDURE:
                ProcedureRecordAuditService.audit(auditLogger, path, eventType, device, aeCache, hl7AppCache);
                break;
            case STUDY:
                StudyRecordAuditService.audit(auditLogger, path, eventType);
                break;
            case PROV_REGISTER:
                ProvideAndRegisterAuditService.audit(auditLogger, path, eventType);
                break;
            case STGCMT:
                StorageCommitAuditService.audit(auditLogger, path, eventType);
                break;
            case INST_RETRIEVED:
                ExternalRetrieveAuditService.audit(auditLogger, path, eventType, aeCache);
                break;
            case LDAP_CHANGES:
                SoftwareConfigurationAuditService.audit(auditLogger, path, eventType);
                break;
            case QUEUE_EVENT:
                TaskAuditService.audit(auditLogger, path, eventType);
                break;
            case IMPAX:
                StoreAuditService.auditImpaxReportPatientMismatch(auditLogger, path, eventType);
                break;
            case QSTAR:
                QStarVerificationAuditService.audit(auditLogger, path, eventType, getArchiveDevice());
                break;
        }
    }

    void spoolApplicationActivity(ArchiveServiceEvent event) {
        String fileName = AuditUtils.EventType.forApplicationActivity(event).name();
        try {
            HttpServletRequest request = event.getRequest();
            if (request == null) {
                writeSpoolFile(fileName, false, new AuditInfoBuilder.Builder()
                                                .calledUserID(device.getDeviceName())
                                                .toAuditInfo());
                return;
            }

            writeSpoolFile(fileName, false, new AuditInfoBuilder.Builder()
                                        .calledUserID(request.getRequestURI())
                                        .callingUserID(KeycloakContext.valueOf(request).getUserName())
                                        .callingHost(request.getRemoteAddr())
                                        .toAuditInfo());
        } catch (Exception e) {
            LOG.info("Failed to spool Application Activity {}\n", event, e);
        }
    }

    private void spoolInstancesRejected(StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        boolean schedulerDeletedExpiredStudies = storeSession.getAssociation() == null
                                                    && storeSession.getHttpRequest() == null;
        String fileName = AuditUtils.EventType.forInstancesRejected(ctx).name();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        try {
            List<AuditInfo> studyRejected = new ArrayList<>();
            List<AuditInfo> sopInstancesRejected = sopInstancesRejectionNote(ctx.getAttributes());
            if (schedulerDeletedExpiredStudies) {
                studyRejected.add(new AuditInfoBuilder.Builder()
                                        .callingUserID(device.getDeviceName())
                                        .addAttrs(ctx.getAttributes(), arcDev)
                                        .pIDAndName(ctx.getAttributes(), arcDev)
                                        .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                                        .warning(ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning())
                                        .toAuditInfo());
                studyRejected.addAll(sopInstancesRejected);
                writeSpoolFile(fileName, false, studyRejected.toArray(new AuditInfo[0]));
                return;
            }

            HttpServletRequestInfo httpServletRequestInfo = storeSession.getHttpRequest();
            String callingUserID = httpServletRequestInfo == null
                                    ? storeSession.getCallingAET() == null
                                        ? storeSession.getLocalApplicationEntity().getAETitle()
                                        : storeSession.getCallingAET()
                                    : httpServletRequestInfo.requesterUserID;
            String calledUserID = httpServletRequestInfo == null
                                    ? storeSession.getCalledAET()
                                    : httpServletRequestInfo.requestURIWithQueryStr();

            studyRejected.add(new AuditInfoBuilder.Builder()
                                    .callingUserID(callingUserID)
                                    .callingHost(storeSession.getRemoteHostName())
                                    .calledUserID(calledUserID)
                                    .addAttrs(ctx.getAttributes(), arcDev)
                                    .pIDAndName(ctx.getAttributes(), arcDev)
                                    .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                                    .warning(ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning())
                                    .toAuditInfo());
            studyRejected.addAll(sopInstancesRejected);
            writeSpoolFile(fileName, false, studyRejected.toArray(new AuditInfo[0]));
        } catch (Exception e) {
            LOG.info("Failed to spool Instances Rejected {}\n", ctx.getStoreSession(), e);
        }
    }

    private void spoolPreviousInstancesDeleted(StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        Study prevStudy = ctx.getPreviousInstance().getSeries().getStudy();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        String fileName = AuditUtils.EventType.forPreviousInstancesDeleted(ctx).name()
                            + '-' + storeSession.getCallingAET()
                            + '-' + storeSession.getCalledAET()
                            + '-' + ctx.getStudyInstanceUID();
        try {
            List<AuditInfo> prevInstancesDeleted = new ArrayList<>();
            prevInstancesDeleted.add(new AuditInfoBuilder.Builder()
                                            .callingHost(storeSession.getRemoteHostName())
                                            .callingUserID(storeSession.getCallingAET())
                                            .calledUserID(storeSession.getCalledAET())
                                            .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                                            .addAttrs(prevStudy.getAttributes(), arcDev)
                                            .pIDAndName(prevStudy.getPatient().getAttributes(), arcDev)
                                            .toAuditInfo());
            prevInstancesDeleted.add(new AuditInfoBuilder.Builder()
                                            .sopCUID(ctx.getAttributes().getString(Tag.SOPClassUID))
                                            .sopIUID(ctx.getAttributes().getString(Tag.SOPInstanceUID))
                                            .toAuditInfo());
            writeSpoolFile(fileName, true, prevInstancesDeleted.toArray(new AuditInfo[0]));
        } catch (Exception e) {
            LOG.info("Failed to spool previous instances deleted {}\n", storeSession, e);
        }
    }

    void spoolStudyDeleted(StudyDeleteContext ctx) {
        String fileName = AuditUtils.EventType.forStudyDeleted(ctx).name();
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        Study study = ctx.getStudy();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        try {
            List<AuditInfo> studyDeleted = new ArrayList<>();
            List<AuditInfo> sopInstancesDeleted = sopInstancesDeleted(ctx);
            if (httpServletRequestInfo == null) {
                studyDeleted.add(new AuditInfoBuilder.Builder()
                                                .callingUserID(device.getDeviceName())
                                                .addAttrs(study.getAttributes(), arcDev)
                                                .pIDAndName(study.getPatient().getAttributes(), arcDev)
                                                .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                                                .toAuditInfo());
                studyDeleted.addAll(sopInstancesDeleted);
                writeSpoolFile(fileName, false, studyDeleted.toArray(new AuditInfo[0]));
                return;
            }

            studyDeleted.add(new AuditInfoBuilder.Builder()
                    .callingUserID(httpServletRequestInfo.requesterUserID)
                    .callingHost(httpServletRequestInfo.requesterHost)
                    .calledUserID(httpServletRequestInfo.requestURIWithQueryStr())
                    .addAttrs(study.getAttributes(), arcDev)
                    .pIDAndName(study.getPatient().getAttributes(), arcDev)
                    .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                    .toAuditInfo());
            studyDeleted.addAll(sopInstancesDeleted);
            writeSpoolFile(fileName, false, studyDeleted.toArray(new AuditInfo[0]));
        } catch (Exception e) {
            LOG.info("Failed to spool Study Deleted for {}\n", ctx.getStudy(), e);
        }
    }

    private List<AuditInfo> sopInstancesDeleted(StudyDeleteContext ctx) {
        List<AuditInfo> sopInstancesDeleted = new ArrayList<>();
        ctx.getInstances().forEach(instance -> {
            sopInstancesDeleted.add(new AuditInfoBuilder.Builder()
                    .sopCUID(instance.getSopClassUID())
                    .sopIUID(instance.getSopInstanceUID())
                    .toAuditInfo());
        });
        return sopInstancesDeleted;
    }

    void spoolExternalRejection(RejectionNoteSent rejectionNoteSent) {
        String fileName = AuditUtils.EventType.forExternalRejection(rejectionNoteSent).name();
        Attributes attrs = rejectionNoteSent.getRejectionNote();
        HttpServletRequest req = rejectionNoteSent.getRequest();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        try {
            Attributes codeItem = attrs.getSequence(Tag.ConceptNameCodeSequence).get(0);
            List<AuditInfo> studyRejectionNoteSent = new ArrayList<>();
            studyRejectionNoteSent.add(new AuditInfoBuilder.Builder()
                                        .callingUserID(KeycloakContext.valueOf(req).getUserName())
                                        .callingHost(req.getRemoteHost())
                                        .calledUserID(req.getRequestURI())
                                        .destUserID(rejectionNoteSent.getRemoteAE().getAETitle())
                                        .destNapID(rejectionNoteSent.getRemoteAE().getConnections().get(0).getHostname())
                                        .outcome(rejectionNoteSent.getErrorComment())
                                        .warning(codeItem.getString(Tag.CodeMeaning))
                                        .addAttrs(attrs, arcDev)
                                        .pIDAndName(attrs, arcDev)
                                        .toAuditInfo());
            studyRejectionNoteSent.addAll(sopInstancesRejectionNote(rejectionNoteSent.getRejectionNote()));
            writeSpoolFile(fileName, false, studyRejectionNoteSent.toArray(new AuditInfo[0]));
        } catch (Exception e) {
            LOG.info("Failed to spool External Rejection {}\n", rejectionNoteSent, e);
        }
    }

    private List<AuditInfo> sopInstancesRejectionNote(Attributes attrs) {
        List<AuditInfo> sopInstancesRejectionNoteSent = new ArrayList<>();
        attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence)
                .forEach(studyRef ->
                    studyRef.getSequence(Tag.ReferencedSeriesSequence).forEach(seriesRef ->
                        seriesRef.getSequence(Tag.ReferencedSOPSequence).forEach(sopRef ->
                                sopInstancesRejectionNoteSent.add(new AuditInfoBuilder.Builder()
                                        .sopCUID(sopRef.getString(Tag.ReferencedSOPClassUID))
                                        .sopIUID(sopRef.getString(Tag.ReferencedSOPInstanceUID))
                                        .toAuditInfo())
                        )
                )
        );
        return sopInstancesRejectionNoteSent;
    }

    void spoolTaskEvent(TaskEvent taskEvent) {
        if (taskEvent.getTask() == null)
            return;

        Task task = taskEvent.getTask();
        try {
            String fileName = AuditUtils.EventType.forTaskEvent(taskEvent.getOperation()).name();
            HttpServletRequestInfo httpServletRequestInfo = HttpServletRequestInfo.valueOf(taskEvent.getRequest());
            AuditInfo taskAuditInfo = new AuditInfoBuilder.Builder()
                    .callingUserID(httpServletRequestInfo.requesterUserID)
                    .callingHost(httpServletRequestInfo.requesterHost)
                    .calledUserID(httpServletRequestInfo.requestURI)
                    .outcome(taskEvent.getException() == null ? null : taskEvent.getException().getMessage())
                    .task(TaskAuditService.toString(task))
                    .taskPOID(Long.toString(task.getPk()))
                    .toAuditInfo();
            writeSpoolFile(fileName, false, taskAuditInfo);
        } catch (Exception e) {
            LOG.info("Failed to spool {} for {} \n", taskEvent, task, e);
        }
    }

    void spoolBulkTasksEvent(BulkTaskEvent bulkTasksEvent) {
        HttpServletRequest req = bulkTasksEvent.getRequest();
        try {
            String fileName = AuditUtils.EventType.forTaskEvent(bulkTasksEvent.getOperation()).name();
            if (req == null) {
                writeSpoolFile(fileName, false,
                        new AuditInfoBuilder.Builder()
                            .callingUserID(device.getDeviceName())
                            .outcome(bulkTasksEvent.getException() == null ? null : bulkTasksEvent.getException().getMessage())
                            .count(bulkTasksEvent.getCount())
                            .failed(bulkTasksEvent.getFailed())
                            .taskPOID(bulkTasksEvent.getOperation().name())
                            .queueName(bulkTasksEvent.getQueueName())
                            .toAuditInfo());
                return;
            }

            HttpServletRequestInfo httpServletRequestInfo = HttpServletRequestInfo.valueOf(req);
            writeSpoolFile(fileName, false,
                    new AuditInfoBuilder.Builder()
                            .callingUserID(httpServletRequestInfo.requesterUserID)
                            .callingHost(httpServletRequestInfo.requesterHost)
                            .calledUserID(httpServletRequestInfo.requestURI)
                            .outcome(bulkTasksEvent.getException() == null ? null : bulkTasksEvent.getException().getMessage())
                            .count(bulkTasksEvent.getCount())
                            .failed(bulkTasksEvent.getFailed())
                            .taskPOID(bulkTasksEvent.getOperation().name())
                            .filters(httpServletRequestInfo.queryString)
                            .toAuditInfo());
        } catch (Exception e) {
            LOG.info("Failed to spool {} \n", bulkTasksEvent, e);
        }
    }

    void spoolSoftwareConfiguration(SoftwareConfiguration softwareConfiguration) {
        try {
            if (softwareConfiguration.getRequest() == null) {
                writeSpoolFile(AuditUtils.EventType.LDAP_CHNGS.name(),
                        new AuditInfoBuilder.Builder()
                                .archiveUserID(softwareConfiguration.getDeviceName())
                                .toAuditInfo(),
                        softwareConfiguration.getLdapDiff().toString().getBytes());
                return;
            }

            HttpServletRequestInfo httpServletRequestInfo = HttpServletRequestInfo.valueOf(softwareConfiguration.getRequest());
            writeSpoolFile(AuditUtils.EventType.LDAP_CHNGS.name(),
                    new AuditInfoBuilder.Builder()
                            .callingUserID(httpServletRequestInfo.requesterUserID)
                            .callingHost(httpServletRequestInfo.requesterHost)
                            .calledUserID(httpServletRequestInfo.requestURI)
                            .archiveUserID(softwareConfiguration.getDeviceName())
                            .toAuditInfo(),
                    softwareConfiguration.getLdapDiff().toString().getBytes());
        } catch (Exception e) {
            LOG.info("Failed to spool Software Configuration Changes for [Device={}] \n",
                    softwareConfiguration.getDeviceName(), e);
        }
    }

    void spoolExternalRetrieve(ExternalRetrieveContext ctx) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        String warning = ctx.warning() > 0
                            ? "Number Of Warning Sub operations : " + ctx.warning()
                            : null;
        int failed = ctx.failed();
        String outcome = failed > 0
                            ? ctx.getResponse().getString(Tag.ErrorComment) != null
                                ? ctx.getResponse().getString(Tag.ErrorComment) + " : " + failed
                                : "Number Of Failed Sub operations : " + failed
                            : ctx.getResponse().getString(Tag.ErrorComment) != null
                                ? ctx.getResponse().getString(Tag.ErrorComment)
                                : null;
        try {
            if (httpServletRequestInfo == null) {
                AuditInfo auditInfo = new AuditInfoBuilder.Builder()
                                            .callingUserID(ctx.getLocalAET())
                                            .cMoveOriginator(ctx.getRemoteAET())
                                            .calledHost(ctx.getRemoteHostName())
                                            .destUserID(ctx.getDestinationAET())
                                            .addAttrs(ctx.getKeys(), getArchiveDevice())
                                            .warning(warning)
                                            .outcome(outcome)
                                            .errorCode(Integer.parseInt(ctx.getResponse().getString(Tag.Status)))
                                            .toAuditInfo();
                writeSpoolFile(AuditUtils.EventType.INST_RETRV.name(), false, auditInfo);
                return;
            }

            AuditInfo auditInfoREST = new AuditInfoBuilder.Builder()
                                            .callingUserID(httpServletRequestInfo.requesterUserID)
                                            .callingHost(httpServletRequestInfo.requesterHost)
                                            .cMoveOriginator(ctx.getRemoteAET())
                                            .calledHost(ctx.getRemoteHostName())
                                            .calledUserID(httpServletRequestInfo.requestURI)
                                            .queryString(httpServletRequestInfo.queryString)
                                            .destUserID(ctx.getDestinationAET())
                                            .addAttrs(ctx.getKeys(), getArchiveDevice())
                                            .warning(warning)
                                            .outcome(outcome)
                                            .errorCode(Integer.parseInt(ctx.getResponse().getString(Tag.Status)))
                                            .toAuditInfo();
            writeSpoolFile(AuditUtils.EventType.INST_RETRV.name(), false, auditInfoREST);
        } catch (Exception e) {
            LOG.info("Failed to spool External Retrieve {}\n", ctx, e);
        }
    }

    void spoolConnectionFailure(ConnectionEvent event) {
        Connection remoteConnection = event.getRemoteConnection();
        Connection connection = event.getConnection();
        if (remoteConnection.getProtocol().name().startsWith("SYSLOG")) {
            LOG.info("Suppress audits of connection failures to audit record repository : {}",
                    remoteConnection.getDevice());
            return;
        }

        try {
            if (event.getType() == ConnectionEvent.Type.FAILED) {
                writeSpoolFile(AuditUtils.EventType.CONN_FAILR.name(), false,
                        new AuditInfoBuilder.Builder()
                                .callingUserID(connection.getDevice().getDeviceName())
                                .callingHost(connection.getHostname())
                                .calledUserID(remoteConnection.getDevice().getDeviceName())
                                .calledHost(remoteConnection.getHostname())
                                .outcome(event.getException().getMessage())
                                .serviceEventType(event.getType().name())
                                .toAuditInfo());
                return;
            }
            String callingUser = event.getSocket().getRemoteSocketAddress().toString();
            writeSpoolFile(AuditUtils.EventType.CONN_FAILR.name(), false,
                        new AuditInfoBuilder.Builder()
                                .callingUserID(callingUser)
                                .callingHost(callingUser)
                                .calledUserID(connection.getDevice().getDeviceName())
                                .calledHost(connection.getHostname())
                                .outcome(event.getException().getMessage())
                                .serviceEventType(event.getType().name())
                                .toAuditInfo());
        } catch (Exception e) {
            LOG.info("Failed to spool ConnectionEvent[type={}, connection={}, remoteConnection={}]\n",
                    event.getType(), connection, remoteConnection, e);
        }
    }

    void spoolAssociationFailure(AssociationEvent event) {
        Association association = event.getAssociation();
        try {
            if (event.getType() == AssociationEvent.Type.FAILED) {
                writeSpoolFile(AuditUtils.EventType.ASSOC_FAIL.name(), false,
                        new AuditInfoBuilder.Builder()
                                .callingUserID(association.getLocalAET())
                                .callingHost(association.getConnection().getHostname())
                                .calledUserID(association.getRemoteAET())
                                .calledHost(ReverseDNS.hostNameOf(association.getSocket().getInetAddress()))
                                .outcome(event.getException().getMessage())
                                .serviceEventType(event.getType().name())
                                .toAuditInfo());
                return;
            }

            writeSpoolFile(AuditUtils.EventType.ASSOC_FAIL.name(), false,
                    new AuditInfoBuilder.Builder()
                            .callingUserID(association.getRemoteAET())
                            .callingHost(ReverseDNS.hostNameOf(association.getSocket().getInetAddress()))
                            .calledUserID(association.getLocalAET())
                            .calledHost(association.getConnection().getHostname())
                            .outcome(event.getException().getMessage())
                            .serviceEventType(event.getType().name())
                            .toAuditInfo());
        } catch (Exception e) {
            LOG.info("Failed to spool AssociationEvent[type={}, association={}]\n",
                    event.getType(), association, e);
        }
    }

    void spoolStudySizeEvent(StudySizeEvent event) {
        try {
            AuditInfo auditInfo = new AuditInfoBuilder.Builder()
                                        .callingUserID(device.getDeviceName())
                                        .addAttrs(event.getStudy().getAttributes(), getArchiveDevice())
                                        .pIDAndName(event.getStudy().getPatient().getAttributes(), getArchiveDevice())
                                        .toAuditInfo();
            writeSpoolFile(AuditUtils.EventType.STUDY_READ.name(), false, auditInfo);
        } catch (Exception e) {
            LOG.info("Failed to spool {}\n", event, e);
        }
    }

    void spoolPDQ(PDQServiceContext ctx) {
        try {
            if (ctx.getFhirWebAppName() == null) {
                writeSpoolFile(AuditUtils.EventType.PAT_DEMO_Q.name(),
                        PDQAuditService.auditInfo(ctx, getArchiveDevice()),
                        ctx.getHl7Msg().data(),
                        ctx.getRsp().data());
                return;
            }

            writeSpoolFile(AuditUtils.EventType.FHIR___PDQ.name(),
                        false,
                        PDQAuditService.auditInfoFHIR(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.info("Failed to spool PDQ for {}", ctx);
        }
    }

    void spoolQuery(QueryContext ctx) {
        if (ctx.getAssociation() == null && ctx.getHttpRequest() == null)
            return;

        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpRequest();
        AuditInfo auditInfo = httpServletRequestInfo == null
                                ? new AuditInfoBuilder.Builder()
                                        .callingHost(ctx.getRemoteHostName())
                                        .callingUserID(ctx.getCallingAET())
                                        .calledUserID(ctx.getCalledAET())
                                        .queryPOID(ctx.getSOPClassUID())
                                        .toAuditInfo()
                                : new AuditInfoBuilder.Builder()
                                        .callingHost(ctx.getRemoteHostName())
                                        .callingUserID(httpServletRequestInfo.requesterUserID)
                                        .calledUserID(httpServletRequestInfo.requestURI)
                                        .queryPOID(ctx.getSearchMethod())
                                        .query(httpServletRequestInfo.query)
                                        .toAuditInfo();
        writeQuerySpoolFile(auditInfo, ctx);
    }

    private void writeQuerySpoolFile(AuditInfo auditInfo, QueryContext ctx) {
        FileTime eventTime = null;
        try {
            for (AuditLogger auditLogger : auditLoggersForQuery(ctx)) {
                Path directory = toDirPath(auditLogger);
                try {
                    Files.createDirectories(directory);
                    Path file = Files.createTempFile(directory, AuditUtils.EventType.QUERY__EVT.name(), null);
                    try (BufferedOutputStream out = new BufferedOutputStream(
                            Files.newOutputStream(file, StandardOpenOption.APPEND))) {
                        new DataOutputStream(out).writeUTF(auditInfo.toString());
                        if (ctx.getAssociation() != null) {
                            try (DicomOutputStream dos = new DicomOutputStream(out, UID.ImplicitVRLittleEndian)) {
                                dos.writeDataset(null, ctx.getQueryKeys());
                            } catch (Exception e) {
                                LOG.info("Failed to create DicomOutputStream.\n", e);
                            }
                        }
                    }

                    if (eventTime == null)
                        eventTime = Files.getLastModifiedTime(file);
                    else
                        Files.setLastModifiedTime(file, eventTime);

                    if (!getArchiveDevice().isAuditAggregate())
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.info("Failed to write to Query Audit Spool File at [AuditLogger={}]\n",
                            auditLogger.getCommonName(), e);
                }
            }
        } catch (Exception e) {
            LOG.info("Failed to spool Query\n", e);
        }
    }

    private List<AuditLogger> auditLoggersForQuery(QueryContext ctx) {
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        if (ext == null)
            return Collections.emptyList();

        return ext.getAuditLoggers().stream()
                .filter(auditLogger -> auditLogger.isInstalled()
                                        && !auditLogger.isAuditMessageSuppressed(minimalAuditMsgForQuery(ctx)))
                .collect(Collectors.toList());
    }

    private AuditMessage minimalAuditMsgForQuery(QueryContext ctx) {
        AuditMessage msg = new AuditMessage();
        EventIdentification ei = new EventIdentification();
        ei.setEventID(AuditMessages.EventID.Query);
        ei.setEventActionCode(AuditMessages.EventActionCode.Execute);
        ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.Success);
        msg.setEventIdentification(ei);

        ActiveParticipant ap = new ActiveParticipant();
        ap.setUserID(ctx.getCallingAET());
        ap.setUserIsRequestor(true);
        msg.getActiveParticipant().add(ap);
        return msg;
    }

    void auditAndProcessFile(AuditLogger auditLogger, Path file) {
        try {
            aggregateAuditMessage(auditLogger, file);
            Files.delete(file);
        } catch (Exception e) {
            LOG.info("Failed to process [AuditSpoolFile={}] of [AuditLogger={}].\n",
                    file, auditLogger.getCommonName(), e);
            try {
                Files.move(file, file.resolveSibling(file.getFileName().toString() + ".failed"));
            } catch (IOException e1) {
                LOG.info("Failed to mark [AuditSpoolFile={}] of [AuditLogger={}] as failed.\n",
                        file, auditLogger.getCommonName(), e1);
            }
        }
    }

    private void auditQuery(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) throws Exception {
        if (eventType.eventTypeCode == null) {
            QueryAuditService.audit(auditLogger, path, eventType);
            return;
        }

        if (eventType.eventTypeCode == AuditMessages.EventTypeCode.ITI_78_MobilePDQ) {
            PDQAuditService.auditFHIRPDQ(auditLogger, path, eventType, webAppCache);
            return;
        }

        PDQAuditService.auditHL7PDQ(auditLogger, path, eventType, hl7AppCache, device);
    }

    void spoolStoreEvent(StoreContext ctx) {
        try {
            if (ctx.getRejectedInstance() != null) {
                LOG.info("Suppress audit on receive of instances rejected by a previous received Rejection Note : {}",
                        ctx.getRejectedInstance());
                return;
            }

            RejectionNote rejectionNote = ctx.getRejectionNote();
            if (rejectionNote != null && !rejectionNote.isRevokeRejection()) {
                spoolInstancesRejected(ctx);
                return;
            }

            if (isDuplicateReceivedInstance(ctx)) {
                if (rejectionNote != null && rejectionNote.isRevokeRejection())
                    spoolInstancesStored(ctx);
                return;
            }

            if (ctx.getAttributes() == null) {
                LOG.info("Instances stored is not audited as store context attributes are not set.\n",
                        ctx.getException());
                return;
            }

            spoolInstancesStored(ctx);
        } catch (Exception e) {
            LOG.info("Failed to spool Store Event.\n", e);
        }
    }

    private void spoolInstancesStored(StoreContext ctx) {
        if (ctx.getPreviousInstance() != null
                && ctx.getPreviousInstance().getSopInstanceUID().equals(ctx.getStoredInstance().getSopInstanceUID()))
            spoolPreviousInstancesDeleted(ctx);

        AuditUtils.EventType eventType = AuditUtils.EventType.forInstanceStored(ctx);
        StoreSession storeSession = ctx.getStoreSession();
        try {
            AuditInfo instanceInfo = new AuditInfoBuilder.Builder()
                    .sopCUID(ctx.getSopClassUID())
                    .sopIUID(ctx.getSopInstanceUID())
                    .mppsUID(ctx.getMppsInstanceUID())
                    .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                    .errorCode(ctx.getException())
                    .toAuditInfo();

            if (storeSession.getImpaxReportEndpoint() != null) {
                spoolInstancesStoredImpaxReport(instanceInfo, eventType, ctx);
                return;
            }

            if (storeSession.getHttpRequest() != null)
                spoolInstancesStoredBySTOW(instanceInfo, eventType, ctx);
            else if (storeSession.getUnparsedHL7Message() != null)
                spoolInstancesStoredByHL7(instanceInfo, eventType, ctx);
            else
                spoolInstancesStoredByCStore(instanceInfo, eventType, ctx);
        } catch (Exception e) {
            LOG.info("Failed to spool Instances Stored for [StudyIUID={}] triggered by {}\n",
                    ctx.getStudyInstanceUID(), storeSession, e);
        }
    }

    private void spoolInstancesStoredByCStore(AuditInfo instanceInfo, AuditUtils.EventType eventType, StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        Attributes attrs = ctx.getAttributes();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        AuditInfo auditInfo = new AuditInfoBuilder.Builder()
                                    .callingHost(storeSession.getRemoteHostName())
                                    .callingUserID(storeSession.getCallingAET())
                                    .calledUserID(storeSession.getCalledAET())
                                    .addAttrs(attrs, arcDev)
                                    .pIDAndName(attrs, arcDev)
                                    .toAuditInfo();
        String fileName = eventType.name()
                            + '-' + storeSession.getCallingAET()
                            + '-' + storeSession.getCalledAET()
                            + '-' + ctx.getStudyInstanceUID();
        if (ctx.getException() != null)
            fileName += "_ERROR";

        writeSpoolFile(fileName, true, auditInfo, instanceInfo);
    }

    private void spoolInstancesStoredBySTOW(AuditInfo instanceInfo, AuditUtils.EventType eventType, StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        HttpServletRequestInfo httpServletRequestInfo = storeSession.getHttpRequest();
        Attributes attrs = ctx.getAttributes();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        AuditInfo auditInfo = new AuditInfoBuilder.Builder()
                                    .callingHost(storeSession.getRemoteHostName())
                                    .callingUserID(httpServletRequestInfo.requesterUserID)
                                    .calledUserID(httpServletRequestInfo.requestURIWithQueryStr())
                                    .addAttrs(attrs, arcDev)
                                    .pIDAndName(attrs, arcDev)
                                    .toAuditInfo();
        String fileName = eventType.name()
                            + '-' + httpServletRequestInfo.requesterUserID
                            + '-' + storeSession.getCalledAET()
                            + '-' + ctx.getStudyInstanceUID();
        if (ctx.getException() != null)
            fileName += "_ERROR";

        writeSpoolFile(fileName, true, auditInfo, instanceInfo);
    }

    private void spoolInstancesStoredByHL7(AuditInfo instanceInfo, AuditUtils.EventType eventType, StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        Attributes attrs = ctx.getAttributes();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        HL7Segment msh = storeSession.getUnparsedHL7Message().msh();
        AuditInfo auditInfo = new AuditInfoBuilder.Builder()
                                    .callingHost(storeSession.getRemoteHostName())
                                    .callingUserID(msh.getSendingApplicationWithFacility())
                                    .calledUserID(msh.getReceivingApplicationWithFacility())
                                    .addAttrs(attrs, arcDev)
                                    .pIDAndName(attrs, arcDev)
                                    .toAuditInfo();
        String fileName = eventType.name()
                            + '-' + msh.getSendingApplicationWithFacility().replace('|', '-')
                            + '-' + msh.getReceivingApplicationWithFacility().replace('|', '-')
                            + '-' + ctx.getStudyInstanceUID();
        if (ctx.getException() != null)
            fileName += "_ERROR";

        writeSpoolFile(fileName, true, auditInfo, instanceInfo);
    }

    private void spoolInstancesStoredImpaxReport(AuditInfo instanceInfo, AuditUtils.EventType eventType, StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        HttpServletRequestInfo httpServletRequestInfo = storeSession.getHttpRequest();
        Attributes attrs = ctx.getAttributes();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);

        AuditInfo auditInfoStore = httpServletRequestInfo == null
                                    ? new AuditInfoBuilder.Builder()
                                        .callingUserID(device.getDeviceName())
                                        .calledUserID(storeSession.getCalledAET())
                                        .addAttrs(attrs, arcDev)
                                        .pIDAndName(attrs, arcDev)
                                        .impaxEndpoint(storeSession.getImpaxReportEndpoint())
                                        .toAuditInfo()
                                    : new AuditInfoBuilder.Builder()
                                        .callingHost(storeSession.getRemoteHostName())
                                        .callingUserID(httpServletRequestInfo.requesterUserID)
                                        .calledUserID(httpServletRequestInfo.requestURIWithQueryStr())
                                        .addAttrs(attrs, arcDev)
                                        .pIDAndName(attrs, arcDev)
                                        .impaxEndpoint(storeSession.getImpaxReportEndpoint())
                                        .toAuditInfo();

        String fileName = eventType.name()
                            + '-' + (httpServletRequestInfo == null
                                        ? device.getDeviceName()
                                        : httpServletRequestInfo.requesterUserID)
                            + '-' + storeSession.getCalledAET()
                            + '-' + ctx.getStudyInstanceUID();
        if (ctx.getException() != null)
            fileName += "_ERROR";

        writeSpoolFile(fileName, true, auditInfoStore, instanceInfo);
        if (ctx.getImpaxReportPatientMismatch() != null)
            spoolImpaxReportPatientMismatch(ctx, instanceInfo);
    }

    private void spoolImpaxReportPatientMismatch(StoreContext ctx, AuditInfo instanceInfo) {
        StoreSession storeSession = ctx.getStoreSession();
        Attributes attrs = ctx.getAttributes();
        ArchiveDeviceExtension arcDev = getArchiveDevice();
        HttpServletRequestInfo httpServletRequestInfo = storeSession.getHttpRequest();
        AuditInfo auditInfoPatientMismatch = httpServletRequestInfo == null
                                                ? new AuditInfoBuilder.Builder()
                                                    .callingUserID(device.getDeviceName())
                                                    .addAttrs(attrs, arcDev)
                                                    .pIDAndName(attrs, arcDev)
                                                    .impaxEndpoint(storeSession.getImpaxReportEndpoint())
                                                    .patMismatchCode(ctx.getImpaxReportPatientMismatch().toString())
                                                    .toAuditInfo()
                                                : new AuditInfoBuilder.Builder()
                                                    .callingUserID(httpServletRequestInfo.requesterUserID)
                                                    .callingHost(storeSession.getRemoteHostName())
                                                    .calledUserID(httpServletRequestInfo.requestURIWithQueryStr())
                                                    .addAttrs(attrs, arcDev)
                                                    .pIDAndName(attrs, arcDev)
                                                    .impaxEndpoint(storeSession.getImpaxReportEndpoint())
                                                    .patMismatchCode(ctx.getImpaxReportPatientMismatch().toString())
                                                    .toAuditInfo();
        writeSpoolFile(AuditUtils.EventType.IMPAX_MISM.name(), false, auditInfoPatientMismatch, instanceInfo);
    }

    private boolean isDuplicateReceivedInstance(StoreContext ctx) {
        return ctx.getLocations().isEmpty() && ctx.getStoredInstance() == null && ctx.getException() == null;
    }

    void spoolWADOURI(RetrieveContext ctx) {
        if (ctx.getSopInstanceUIDs().length == 0) {
            LOG.info("SOP Instance for auditing Retrieve object by WADO URI not available in retrieve context, exit spooling");
            return;
        }

        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        try {
            Attributes attrs = ctx.getMatches().get(0).getAttributes();
            String fileName = AuditUtils.EventType.WADO___URI.name()
                                + '-' + httpServletRequestInfo.requesterHost
                                + '-' + ctx.getLocalAETitle()
                                + '-' + ctx.getStudyInstanceUIDs()[0];
            AuditInfo info = new AuditInfoBuilder.Builder()
                                .callingUserID(httpServletRequestInfo.requesterUserID)
                                .callingHost(httpServletRequestInfo.requesterHost)
                                .calledUserID(httpServletRequestInfo.requestURIWithQueryStr())
                                .addAttrs(attrs, getArchiveDevice())
                                .pIDAndName(attrs, getArchiveDevice())
                                .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                                .toAuditInfo();
            AuditInfo instanceInfo = new AuditInfoBuilder.Builder()
                                        .sopCUID(attrs.getString(Tag.SOPClassUID))
                                        .sopIUID(ctx.getSopInstanceUIDs()[0])
                                        .toAuditInfo();
            writeSpoolFile(fileName, true, info, instanceInfo);
        } catch (Exception e) {
            LOG.info("Failed to spool Wado URI for [StudyIUID={}] triggered by [User={}]\n",
                    ctx.getStudyInstanceUID(), httpServletRequestInfo.requesterUserID, e);
        }
    }

    void spoolRetrieve(AuditUtils.EventType eventType, RetrieveContext ctx) {
        if (ctx.getMatches().isEmpty()
                && ctx.getCStoreForwards().isEmpty()
                && (ctx.failed() == 0 || ctx.getFailedMatches().isEmpty())) {
            LOG.info("Neither matches nor C-Store Forwards nor failed matches present. Exit spooling retrieve event {}",
                    eventType);
            return;
        }

        try {
            List<AuditInfo> successAuditInfos = RetrieveAuditService.successAuditInfos(ctx, getArchiveDevice());
            if (!successAuditInfos.isEmpty())
                writeSpoolFile(eventType.name(), false, successAuditInfos.toArray(new AuditInfo[0]));

            List<AuditInfo> failedAuditInfos = RetrieveAuditService.failedAuditInfos(ctx, getArchiveDevice());
            if (!failedAuditInfos.isEmpty())
                writeSpoolFile(eventType.name(), false, failedAuditInfos.toArray(new AuditInfo[0]));
        } catch (Exception e) {
            LOG.info("Failed to spool Retrieve of [StudyIUID={}]\n", ctx.getStudyInstanceUID(), e);
        }
    }

    private ArchiveDeviceExtension getArchiveDevice() {
        return device.getDeviceExtension(ArchiveDeviceExtension.class);
    }

    void spoolHL7Message(HL7ConnectionEvent hl7ConnEvent) {
        HL7ConnectionEvent.Type type = hl7ConnEvent.getType();
        if (hl7ConnEvent.getHL7ResponseMessage() == null
                || type == HL7ConnectionEvent.Type.MESSAGE_SENT
                || type == HL7ConnectionEvent.Type.MESSAGE_RECEIVED)
            return;

        if (type == HL7ConnectionEvent.Type.MESSAGE_PROCESSED)
            spoolIncomingHL7Msg(hl7ConnEvent);
        if (type == HL7ConnectionEvent.Type.MESSAGE_RESPONSE)
            spoolOutgoingHL7Msg(hl7ConnEvent);
    }

    private void spoolIncomingHL7Msg(HL7ConnectionEvent hl7ConnEvent) {
        byte[][] hl7MsgAndResponse = hl7MsgAndResponse(hl7ConnEvent).toArray(new byte[0][]);
        HL7Message hl7Message = toHL7Message(hl7ConnEvent.getHL7Message());
        spoolIncomingHL7MsgForPatientRecord(hl7ConnEvent, hl7Message, hl7MsgAndResponse);
        spoolIncomingHL7MsgForProcedureRecord(hl7ConnEvent, hl7Message, hl7MsgAndResponse);
    }

    private void spoolIncomingHL7MsgForPatientRecord(
            HL7ConnectionEvent hl7ConnEvent, HL7Message hl7Message, byte[][] hl7MsgAndResponse) {
        UnparsedHL7Message unparsedHL7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = unparsedHL7Message.msh();
        HL7Segment pid = hl7Message.getSegment(AuditUtils.PID_SEGMENT);
        if (pid == null) {
            LOG.info("Exit spooling incoming HL7 message for Patient Record audit - No PID segment in HL7 message {}",
                    hl7Message);
            return;
        }

        ArchiveDeviceExtension arcDev = getArchiveDevice();
        String messageType = msh.getMessageType();
        try {
            AuditUtils.EventType eventType = AuditUtils.EventType.forHL7IncomingPatientRecord(hl7ConnEvent);
            writeSpoolFile(eventType.name(),
                    PatientRecordAuditService.patientAuditInfoHL7ForHL7Incoming(hl7ConnEvent, hl7Message, arcDev),
                    hl7MsgAndResponse);

            if (messageType.equals(AuditUtils.MERGE_PATIENTS) || messageType.equals(AuditUtils.CHANGE_PATIENT_ID))
                writeSpoolFile(AuditUtils.EventType.PAT_DELETE.name(),
                        PatientRecordAuditService.prevPatientAuditInfoHL7ForHL7Incoming(hl7ConnEvent, hl7Message, arcDev),
                        hl7MsgAndResponse);
        } catch (Exception e) {
            LOG.info("Failed to spool patient record for incoming HL7 message {}\n", hl7Message, e);
        }
    }

    private void spoolIncomingHL7MsgForProcedureRecord(
            HL7ConnectionEvent hl7ConnEvent, HL7Message hl7Message, byte[][] hl7MsgAndResponse) {
        UnparsedHL7Message unparsedHL7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = unparsedHL7Message.msh();
        String messageType = msh.getMessageType();
        if (!Arrays.asList(AuditUtils.ORDER_MESSAGES).contains(messageType))
            return;

        try {
            AuditUtils.EventType eventType = AuditUtils.EventType.forHL7IncomingOrderMsg(hl7ConnEvent);
            writeSpoolFile(eventType.name(),
                    ProcedureRecordAuditService.procedureAuditInfoForHL7Incoming(hl7ConnEvent, hl7Message, getArchiveDevice()),
                    hl7MsgAndResponse);
        } catch (Exception e) {
            LOG.info("Failed to spool procedure record for incoming HL7 message {}\n", unparsedHL7Message, e);
        }
    }

    private void spoolOutgoingHL7Msg(HL7ConnectionEvent hl7ConnEvent) {
        byte[][] hl7MsgAndResponse = hl7MsgAndResponse(hl7ConnEvent).toArray(new byte[0][]);
        HL7Message hl7Message = toHL7Message(hl7ConnEvent.getHL7Message());
        spoolOutgoingHL7MsgForPatientRecord(hl7ConnEvent, hl7Message, hl7MsgAndResponse);
        spoolOutgoingHL7MsgForProcedureRecord(hl7ConnEvent, hl7Message, hl7MsgAndResponse);
    }

    private void spoolOutgoingHL7MsgForPatientRecord(
            HL7ConnectionEvent hl7ConnEvent, HL7Message hl7Message, byte[][] hl7MsgAndResponse) {
        HL7Segment pid = hl7Message.getSegment(AuditUtils.PID_SEGMENT);
        if (pid == null) {
            LOG.info("Exit spooling outgoing HL7 message for Patient Record audit - No PID segment in HL7 message {}",
                    hl7Message);
            return;
        }

        ArchiveDeviceExtension arcDev = getArchiveDevice();
        UnparsedHL7Message unparsedHL7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = unparsedHL7Message.msh();
        String messageType = msh.getMessageType();
        try {
            AuditUtils.EventType eventType = AuditUtils.EventType.forHL7OutgoingPatientRecord(hl7ConnEvent);
            writeSpoolFile(eventType.name(),
                    PatientRecordAuditService.patientAuditInfoForHL7Outgoing(hl7ConnEvent, hl7Message, arcDev),
                    hl7MsgAndResponse);

            if (messageType.equals(AuditUtils.MERGE_PATIENTS) || messageType.equals(AuditUtils.CHANGE_PATIENT_ID))
                writeSpoolFile(AuditUtils.EventType.PAT_DELETE.name(),
                        PatientRecordAuditService.prevPatientAuditInfoForHL7Outgoing(hl7ConnEvent, hl7Message, arcDev),
                        hl7MsgAndResponse);
        } catch (Exception e) {
            LOG.info("Failed to spool patient record for incoming HL7 message {}\n", hl7Message, e);
        }
    }

    private void spoolOutgoingHL7MsgForProcedureRecord(
            HL7ConnectionEvent hl7ConnEvent, HL7Message hl7Message, byte[][] hl7MsgAndResponse) {
        UnparsedHL7Message unparsedHL7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = unparsedHL7Message.msh();
        String messageType = msh.getMessageType();
        if (!Arrays.asList(AuditUtils.ORDER_MESSAGES).contains(messageType))
            return;

        try {
            AuditUtils.EventType eventType = AuditUtils.EventType.forHL7OutgoingOrderMsg(hl7Message);
            writeSpoolFile(eventType.name(),
                    ProcedureRecordAuditService.procedureAuditInfoForHL7Outgoing(hl7ConnEvent, hl7Message, getArchiveDevice()),
                    hl7MsgAndResponse);
        } catch (Exception e) {
            LOG.info("Failed to spool procedure record for incoming HL7 message {}\n", unparsedHL7Message, e);
        }
    }

    private HL7Message toHL7Message(UnparsedHL7Message unparsedHL7Message) {
        HL7Segment msh = unparsedHL7Message.msh();
        String charset = msh.getField(17, "ASCII");
        return HL7Message.parse(unparsedHL7Message.unescapeXdddd(), charset);
    }

    void spoolPatientRecord(PatientMgtContext ctx) {
        if (ctx.getUnparsedHL7Message() != null)
            return;

        ArchiveDeviceExtension arcDev = getArchiveDevice();
        AuditUtils.EventType eventType = AuditUtils.EventType.forPatientRecord(ctx);
        if (eventType == null) {
            LOG.info("No Op in Patient Management - EventActionCode={}. Exit spooling", ctx.getEventActionCode());
            return;
        }

        try {
            writeSpoolFile(eventType.name(), false,
                    PatientRecordAuditService.patientAuditInfo(ctx, arcDev));
        } catch (Exception e) {
            LOG.info("Failed to spool Patient Record for [PatientID={}]\n", ctx.getPatientIDs(), e);
        }

        try {
            if (ctx.getPreviousAttributes() != null)
                writeSpoolFile(AuditUtils.EventType.PAT_DELETE.name(),
                        false,
                        PatientRecordAuditService.prevPatientAuditInfo(ctx, arcDev));
        } catch (Exception e) {
            LOG.info("Failed to spool previous Patient Record for [PatientID={}]\n", ctx.getPreviousPatientIDs(), e);
        }
    }

    void spoolProcedureRecord(ProcedureContext ctx) {
        if (ctx.getUnparsedHL7Message() != null && !ctx.getUnparsedHL7Message().msh().getMessageType().equals("ADT^A10"))
            return;

        AuditUtils.EventType eventType = AuditUtils.EventType.forProcedure(ctx);
        if (eventType == null) {
            LOG.info("Procedure context fired for an unknown event, skip spooling procedure record for audit");
            return;
        }

        try {
            writeSpoolFile(eventType.name(), false,
                    ProcedureRecordAuditService.procedureAuditInfo(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.info("Failed to spool Procedure Update procedure record for EventActionCode={}\n",
                    ctx.getEventActionCode(), e);
        }
    }

    void spoolStudyRecord(StudyMgtContext ctx) {
        if (ctx.getEventActionCode() == null) {
            LOG.info("{} not updated, exit spooling", ctx.getStudy());
            return;
        }

        try {
            if (ctx.getExpirationDate() == null) {
                spoolStudyUpdateRecord(ctx);
                return;
            }
            spoolStudyExpired(ctx);
        } catch (Exception e) {
            LOG.info("Failed to spool Study Update procedure record for {}\n", ctx.getStudy(),  e);
        }
    }

    private void spoolStudyUpdateRecord(StudyMgtContext ctx) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpRequest();
        ArchiveDeviceExtension arcDev = getArchiveDevice();
        Study study = ctx.getStudy();
        Patient patient = ctx.getPatient();
        AuditInfo auditInfo = new AuditInfoBuilder.Builder()
                                    .callingUserID(httpServletRequestInfo.requesterUserID)
                                    .callingHost(ctx.getRemoteHostName())
                                    .calledUserID(httpServletRequestInfo.requestURIWithQueryStr())
                                    .addAttrs((study == null ? ctx.getAttributes() : study.getAttributes()), arcDev)
                                    .pIDAndName((patient == null ? ctx.getAttributes() : patient.getAttributes()), arcDev)
                                    .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                                    .toAuditInfo();
        writeSpoolFile(AuditUtils.EventType.STUDY_UPDT.name(), false, auditInfo);
    }

    private void spoolStudyExpired(StudyMgtContext ctx) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpRequest();
        if (httpServletRequestInfo == null) {
            spoolStudyExpiredByHL7(ctx);
            return;
        }

        ArchiveDeviceExtension arcDev = getArchiveDevice();
        AuditInfo auditInfo = new AuditInfoBuilder.Builder()
                                    .callingUserID(httpServletRequestInfo.requesterUserID)
                                    .callingHost(ctx.getRemoteHostName())
                                    .calledUserID(httpServletRequestInfo.requestURIWithQueryStr())
                                    .addAttrs(ctx.getStudy().getAttributes(), arcDev)
                                    .pIDAndName(ctx.getPatient().getAttributes(), arcDev)
                                    .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                                    .expirationDate(ctx.getExpirationDate().toString())
                                    .toAuditInfo();
        writeSpoolFile(AuditUtils.EventType.STUDY_UPDT.name(), false, auditInfo);
    }

    private void spoolStudyExpiredByHL7(StudyMgtContext ctx) {
        HL7Segment msh = ctx.getUnparsedHL7Message().msh();
        ArchiveDeviceExtension arcDev = getArchiveDevice();
        AuditInfo auditInfo = new AuditInfoBuilder.Builder()
                                    .callingUserID(msh.getSendingApplicationWithFacility())
                                    .callingHost(ctx.getRemoteHostName())
                                    .calledUserID(msh.getReceivingApplicationWithFacility())
                                    .addAttrs(ctx.getStudy().getAttributes(), arcDev)
                                    .pIDAndName(ctx.getPatient().getAttributes(), arcDev)
                                    .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                                    .expirationDate(ctx.getExpirationDate().toString())
                                    .toAuditInfo();
        writeSpoolFile(AuditUtils.EventType.STUDY_UPDT.name(), false, auditInfo);
    }

    void spoolProvideAndRegister(ExportContext ctx) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        URI destination = ctx.getExporter().getExporterDescriptor().getExportURI();
        String schemeSpecificPart = destination.getSchemeSpecificPart();
        String destinationHost = schemeSpecificPart.substring(schemeSpecificPart.indexOf("://") + 3, schemeSpecificPart.lastIndexOf(":"));
        ArchiveDeviceExtension arcDev = getArchiveDevice();
        try {
            if (httpServletRequestInfo == null) {
                writeSpoolFile(AuditUtils.EventType.PROV_REGIS.name(), false,
                        new AuditInfoBuilder.Builder()
                                .callingUserID(arcDev.getDevice().getDeviceName())
                                .destUserID(destination.toString())
                                .destNapID(destinationHost)
                                .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                                .pIDAndName(ctx.getXDSiManifest(), arcDev)
                                .submissionSetUID(ctx.getSubmissionSetUID())
                                .toAuditInfo());
                return;
            }

            writeSpoolFile(AuditUtils.EventType.PROV_REGIS.name(), false,
                    new AuditInfoBuilder.Builder()
                            .callingUserID(httpServletRequestInfo.requesterUserID)
                            .callingHost(httpServletRequestInfo.requesterHost)
                            .calledUserID(httpServletRequestInfo.requestURI)
                            .queryString(httpServletRequestInfo.queryString)
                            .destUserID(destination.toString())
                            .destNapID(destinationHost)
                            .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                            .pIDAndName(ctx.getXDSiManifest(), arcDev)
                            .submissionSetUID(ctx.getSubmissionSetUID()).toAuditInfo());
        } catch (Exception e) {
            LOG.info("Failed to spool Provide and Register for [SubmissionSetUID={}, XDSiManifest={}]\n",
                    ctx.getSubmissionSetUID(), ctx.getXDSiManifest(), e);
        }
    }

    void spoolStgCmt(StgCmtContext ctx) {
        try {
            List<AuditInfo> successAuditInfos = StorageCommitAuditService.successAuditInfos(ctx, getArchiveDevice());
            if (!successAuditInfos.isEmpty())
                writeSpoolFile(
                        AuditUtils.EventType.STG_COMMIT.name(), false,
                        successAuditInfos.toArray(AuditInfo[]::new));

            List<AuditInfo> failedAuditInfos = StorageCommitAuditService.failedAuditInfos(ctx, getArchiveDevice());
            if (!failedAuditInfos.isEmpty())
                writeSpoolFile(
                        AuditUtils.EventType.STG_COMMIT.name(), false,
                        failedAuditInfos.toArray(AuditInfo[]::new));
        } catch (Exception e) {
            LOG.info("Failed to spool storage commitment.\n", e);
        }
    }

    private Path toDirPath(AuditLogger auditLogger) {
        return Paths.get(
                StringUtils.replaceSystemProperties(getArchiveDevice().getAuditSpoolDirectory()),
                URLEncoder.encode(auditLogger.getCommonName(), StandardCharsets.UTF_8));
    }

    private List<byte[]> hl7MsgAndResponse(HL7ConnectionEvent hl7ConnEvent) {
        List<byte[]> hl7MsgAndResponse = new ArrayList<>();
        int auditHL7MsgLimit = auditHL7MsgLimit(hl7ConnEvent);
        hl7MsgAndResponse.add(limitHL7MsgInAudit(hl7ConnEvent.getHL7Message(), auditHL7MsgLimit));
        hl7MsgAndResponse.add(limitHL7MsgInAudit(hl7ConnEvent.getHL7ResponseMessage(), auditHL7MsgLimit));
        return hl7MsgAndResponse;
    }

    private int auditHL7MsgLimit(HL7ConnectionEvent hl7ConnEvent) {
        UnparsedHL7Message unparsedHL7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = unparsedHL7Message.msh();
        HL7Application hl7Application = device.getDeviceExtension(HL7DeviceExtension.class)
                .getHL7Application(hl7ConnEvent.getType() == HL7ConnectionEvent.Type.MESSAGE_PROCESSED
                                ? msh.getReceivingApplicationWithFacility()
                                : msh.getSendingApplicationWithFacility(),
                        true);
        if (hl7Application == null) {
            LOG.info("No HL7 Application found for HL7ConnectionEvent.Type [name={}] - {}. Use auditHL7MsgLimit value from device.",
                    hl7ConnEvent.getType().name(), msh);
            return device.getDeviceExtension(ArchiveDeviceExtension.class).getAuditHL7MsgLimit();
        }

        return hl7Application.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class).auditHL7MsgLimit();
    }

    private byte[] limitHL7MsgInAudit(UnparsedHL7Message unparsedHL7Msg, int auditHL7MsgLimit) {
        byte[] data = unparsedHL7Msg.data();
        if (data.length <= auditHL7MsgLimit)
            return data;

        LOG.info("HL7 message [MessageHeader={}] length {} greater configured Audit HL7 Message Limit {} - truncate HL7 message in emitted audit",
                unparsedHL7Msg.msh(), data.length, auditHL7MsgLimit);
        byte[] truncatedHL7 = new byte[auditHL7MsgLimit];
        System.arraycopy(data, 0, truncatedHL7, 0, auditHL7MsgLimit - 3);
        System.arraycopy("...".getBytes(), 0, truncatedHL7, auditHL7MsgLimit - 3, 3);
        return truncatedHL7;
    }

    static void emitAuditMessage(
            AuditLogger auditLogger, EventIdentification eventIdentification, List<ActiveParticipant> activeParticipants,
            ParticipantObjectIdentification... participantObjectIdentifications) {
        AuditMessage msg = AuditMessages.createMessage(eventIdentification, activeParticipants, participantObjectIdentifications);
        msg.getAuditSourceIdentification().add(auditLogger.createAuditSourceIdentification());
        try {
            auditLogger.write(auditLogger.timeStamp(), msg);
        } catch (Exception e) {
            LOG.info("Failed to emit audit message for [AuditLogger={}]\n", auditLogger.getCommonName(), e);
        }
    }

    static Calendar getEventTime(Path path, AuditLogger auditLogger){
        Calendar eventTime = auditLogger.timeStamp();
        try {
            eventTime.setTimeInMillis(Files.getLastModifiedTime(path).toMillis());
        } catch (Exception e) {
            LOG.info("Failed to get Last Modified Time of [AuditSpoolFile={}] of [AuditLogger={}]\n",
                    path, auditLogger.getCommonName(), e);
        }
        return eventTime;
    }

    void spoolQStarVerification(QStarVerification qStarVerification) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        try {
            String fileName = AuditUtils.EventType.QSTAR_VERI.name()
                                + "-" + qStarVerification.status
                                + "-" + qStarVerification.seriesPk;
            Set<AuditInfo> auditInfos = new LinkedHashSet<>();
            AuditInfo qStar = new AuditInfoBuilder.Builder()
                                    .callingUserID(device.getDeviceName())
                                    .calledUserID(qStarVerification.filePath)
                                    .outcome(QStarVerificationAuditService.QStarAccessStateEventOutcome.fromQStarVerification(qStarVerification)
                                            .getDescription())
                                    .studyIUID(qStarVerification.studyInstanceUID)
                                    .unknownPID(arcDev)
                                    .toAuditInfo();
            auditInfos.add(qStar);
            qStarVerification.sopRefs.forEach(sopRef ->
                auditInfos.add(new AuditInfoBuilder.Builder()
                                    .sopCUID(sopRef.sopClassUID)
                                    .sopIUID(sopRef.sopInstanceUID)
                                    .toAuditInfo()));
            writeSpoolFile(fileName, true, auditInfos.toArray(new AuditInfo[0]));
        } catch (Exception e) {
            LOG.info("Failed to spool {}", qStarVerification);
        }
    }

    private void writeSpoolFile(String fileName, AuditInfo auditInfo, byte[]... datas) {
        if (auditInfo == null) {
            LOG.info("Attempt to write empty file : " + fileName);
            return;
        }

        FileTime eventTime = null;
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (!auditLogger.isInstalled())
                continue;

            try {
                Path dir = toDirPath(auditLogger);
                Files.createDirectories(dir);
                Path filePath = Files.createTempFile(dir, fileName, null);
                try (BufferedOutputStream out = new BufferedOutputStream(
                        Files.newOutputStream(filePath, StandardOpenOption.APPEND))) {
                    try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(
                            filePath, StandardCharsets.UTF_8, StandardOpenOption.APPEND))) {
                        writer.writeLine(auditInfo);
                    }
                    for (byte[] data : datas)
                        out.write(data);
                }

                if (eventTime == null)
                    eventTime = Files.getLastModifiedTime(filePath);
                else
                    Files.setLastModifiedTime(filePath, eventTime);

                if (!getArchiveDevice().isAuditAggregate())
                    auditAndProcessFile(auditLogger, filePath);
            } catch (Exception e) {
                LOG.info("Failed to write [AuditSpoolFile={}] at [AuditLogger={}]\n",
                        fileName, auditLogger.getCommonName(), e);
            }
        }
    }

    private void writeSpoolFile(String fileName, boolean aggregate, AuditInfo... auditInfos) {
        if (auditInfos == null) {
            LOG.info("Attempt to write empty file : " + fileName);
            return;
        }

        FileTime eventTime = null;
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (!auditLogger.isInstalled())
                continue;

            try {
                Path dir = toDirPath(auditLogger);
                Files.createDirectories(dir);
                Path filePath = aggregate ? dir.resolve(fileName) : Files.createTempFile(dir, fileName, null);
                boolean append = Files.exists(filePath);
                try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(
                        filePath, StandardCharsets.UTF_8, append
                                                            ? StandardOpenOption.APPEND
                                                            : StandardOpenOption.CREATE_NEW))) {
                    if (!append || !aggregate)
                        writer.writeLine(auditInfos[0]);

                    for (int i = 1; i < auditInfos.length; i++)
                        writer.writeLine(auditInfos[i]);
                }

                if (eventTime == null)
                    eventTime = Files.getLastModifiedTime(filePath);
                else
                    Files.setLastModifiedTime(filePath, eventTime);

                if (!getArchiveDevice().isAuditAggregate())
                    auditAndProcessFile(auditLogger, filePath);
            } catch (Exception e) {
                LOG.info("Failed to write [AuditSpoolFile={}] at [AuditLogger={}]\n",
                        fileName, auditLogger.getCommonName(), e);
            }
        }
    }
}