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

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.entity.RejectionState;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.event.ArchiveServiceEvent;
import org.dcm4chee.arc.event.RejectionNoteSent;
import org.dcm4chee.arc.event.TaskOperation;
import org.dcm4chee.arc.hl7.ArchiveHL7Message;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2016
 */

class AuditUtils {
    private static final Logger LOG = LoggerFactory.getLogger(AuditUtils.class);
    final static String APPOINTMENTS = "SIU";
    final static String OBSERVATION_REPORTING = "ORU^R01";
    final static String IMAGING_ORDER = "OMI^O19";
    final static String CREATE_PATIENT = "ADT^A28";
    final static String MERGE_PATIENTS = "ADT^A40";
    final static String CHANGE_PATIENT_ID = "ADT^A47";
    final static String PID_SEGMENT = "PID";
    final static int PID_SEGMENT_PATIENT_ID = 3;
    final static int PID_SEGMENT_PATIENT_NAME = 5;
    final static String ORC_SEGMENT = "ORC";
    final static int ORC_SEGMENT_ORDER_CONTROL = 1;
    final static int ORC_SEGMENT_ORDER_STATUS = 5;
    final static String OBR_SEGMENT = "OBR";
    final static int OBR_SEGMENT_ACCESSION_NO = 18;
    final static int OBR_SEGMENT_REQ_PROC_ID = 19;
    final static String ZDS_SEGMENT = "ZDS";
    final static int ZDS_SEGMENT_STUDY_IUID = 1;
    final static String IPC_SEGMENT = "IPC";
    final static int IPC_SEGMENT_ACCESSION_NO = 1;
    final static int IPC_SEGMENT_REQ_PROC_ID = 2;
    final static int IPC_SEGMENT_STUDY_IUID = 3;
    final static String MRG_SEGMENT = "MRG";
    final static int MRG_SEGMENT_PATIENT_ID = 1;
    final static int MRG_SEGMENT_PATIENT_NAME = 7;
    final static String PDQ_DICOM = "pdq-dicom";
    final static String PDQ_HL7 = "pdq-hl7";
    final static String PDQ_FHIR = "pdq-fhir";
    final static String[] ORDER_MESSAGES = new String[]{"ORM^O01", "OMG^O19", "OMI^O23"};
    final static String[] SPS_SCHEDULED = new String[]{"NW_SC", "NW_IP", "XO_SC"};

    enum EventClass {
        QUERY,
        USER_DELETED,
        SCHEDULER_DELETED,
        STORE_WADOR,
        CONN_FAILURE,
        ASSOCIATION_FAILURE,
        RETRIEVE,
        APPLN_ACTIVITY,
        PATIENT,
        PROCEDURE,
        STUDY,
        PROV_REGISTER,
        STGCMT,
        INST_RETRIEVED,
        LDAP_CHANGES,
        QUEUE_EVENT,
        IMPAX,
        QSTAR
    }
    enum EventType {
        QSTAR_VERI(EventClass.QSTAR, AuditMessages.EventID.Export, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.DestinationMedia, null),
        WADO___URI(EventClass.STORE_WADOR, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        STORE_CREA(EventClass.STORE_WADOR, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Create,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        STORE_UPDT(EventClass.STORE_WADOR, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Update,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),

        RTRV_BEGIN(EventClass.RETRIEVE, AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),

        RTRV___TRF(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),

        STG_COMMIT(EventClass.STGCMT, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),

        INST_RETRV(EventClass.INST_RETRIEVED, AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),

        RJ_PARTIAL(EventClass.USER_DELETED, AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Delete,
                null, null, null),
        RJ_COMPLET(EventClass.USER_DELETED, AuditMessages.EventID.DICOMStudyDeleted, AuditMessages.EventActionCode.Delete,
                null, null, null),

        RJ_SCH_FEW(EventClass.SCHEDULER_DELETED, AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Delete,
                null, null, null),
        PRMDLT_SCH(EventClass.SCHEDULER_DELETED, AuditMessages.EventID.DICOMStudyDeleted, AuditMessages.EventActionCode.Delete,
                null, null, null),

        APPLNSTART(EventClass.APPLN_ACTIVITY, AuditMessages.EventID.ApplicationActivity, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.ApplicationLauncher, AuditMessages.RoleIDCode.Application,
                AuditMessages.EventTypeCode.ApplicationStart),
        APPLN_STOP(EventClass.APPLN_ACTIVITY, AuditMessages.EventID.ApplicationActivity, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.ApplicationLauncher, AuditMessages.RoleIDCode.Application,
                AuditMessages.EventTypeCode.ApplicationStop),

        QUERY__EVT(EventClass.QUERY, AuditMessages.EventID.Query, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),

        CONN_FAILR(EventClass.CONN_FAILURE, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, AuditMessages.EventTypeCode.NodeAuthentication),
        ASSOC_FAIL(EventClass.ASSOCIATION_FAILURE, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, AuditMessages.EventTypeCode.AssociationFailure),

        PAT_CREATE(EventClass.PATIENT, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Create,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        PAT_UPDATE(EventClass.PATIENT, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Update,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        PAT_DELETE(EventClass.PATIENT, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Delete,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        PAT_DLT_SC(EventClass.PATIENT, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Delete,
                null, null, null),
        PAT___READ(EventClass.PATIENT, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        PAT_UPD_SC(EventClass.PATIENT, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Update,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        PAT_RD__SC(EventClass.PATIENT, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Read,
                null, null, null),

        PROC_STD_C(EventClass.PROCEDURE, AuditMessages.EventID.ProcedureRecord, AuditMessages.EventActionCode.Create,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        PROC_STD_U(EventClass.PROCEDURE, AuditMessages.EventID.ProcedureRecord, AuditMessages.EventActionCode.Update,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        PROC_STD_R(EventClass.PROCEDURE, AuditMessages.EventID.ProcedureRecord, AuditMessages.EventActionCode.Read,
                null, null, null),
        PROC_STD_D(EventClass.PROCEDURE, AuditMessages.EventID.ProcedureRecord, AuditMessages.EventActionCode.Delete,
                null, null, null),

        STUDY_UPDT(EventClass.STUDY, AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Update,
                null, null, null),
        STUDY_READ(EventClass.STUDY, AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Read,
                null, null, null),

        PROV_REGIS(EventClass.PROV_REGISTER, AuditMessages.EventID.Export, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination,
                AuditMessages.EventTypeCode.ITI_41_ProvideAndRegisterDocumentSetB),

        LDAP_CHNGS(EventClass.LDAP_CHANGES, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, AuditMessages.EventTypeCode.SoftwareConfiguration),

        IMPAX_MISM(EventClass.IMPAX, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, null),

        CANCEL_TSK(EventClass.QUEUE_EVENT, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, AuditMessages.EventTypeCode.CancelTask),
        RESCHD_TSK(EventClass.QUEUE_EVENT, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, AuditMessages.EventTypeCode.RescheduleTask),
        DELETE_TSK(EventClass.QUEUE_EVENT, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, AuditMessages.EventTypeCode.DeleteTask),

        PAT_DEMO_Q(EventClass.QUERY, AuditMessages.EventID.Query, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination,
                AuditMessages.EventTypeCode.ITI_21_PatientDemographicsQuery),
        FHIR___PDQ(EventClass.QUERY, AuditMessages.EventID.Query, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination,
                AuditMessages.EventTypeCode.ITI_78_MobilePDQ);


        final EventClass eventClass;
        final AuditMessages.EventID eventID;
        final String eventActionCode;
        final AuditMessages.RoleIDCode source;
        final AuditMessages.RoleIDCode destination;
        final AuditMessages.EventTypeCode eventTypeCode;


        EventType(EventClass eventClass, AuditMessages.EventID eventID, String eventActionCode, AuditMessages.RoleIDCode source,
                  AuditMessages.RoleIDCode destination, AuditMessages.EventTypeCode etc) {
            this.eventClass = eventClass;
            this.eventID = eventID;
            this.eventActionCode = eventActionCode;
            this.source = source;
            this.destination = destination;
            this.eventTypeCode = etc;
        }

        static EventType fromFile(Path file) {
            return valueOf(file.getFileName().toString().substring(0, 10));
        }

        static EventType forApplicationActivity(ArchiveServiceEvent event) {
            return event.getType() == ArchiveServiceEvent.Type.STARTED
                    ? APPLNSTART
                    : APPLN_STOP;
        }

        static EventType forInstanceStored(StoreContext ctx) {
            return !ctx.getLocations().isEmpty() && ctx.getPreviousInstance() != null
                        ? STORE_UPDT : STORE_CREA;
        }

        static EventType forPreviousInstancesDeleted(StoreContext ctx) {
            Study storedStudy = ctx.getStoredInstance().getSeries().getStudy();
            Study previousStudy = ctx.getPreviousInstance().getSeries().getStudy();
            return storedStudy.getPk() == previousStudy.getPk() ? RJ_PARTIAL : RJ_COMPLET;
        }

        static EventType forInstancesRejected(StoreContext ctx) {
            StoreSession storeSession = ctx.getStoreSession();
            boolean isSchedulerDeletedExpiredStudies = storeSession.getAssociation() == null
                                                        && storeSession.getHttpRequest() == null;
            RejectionState rejectionState = ctx.getStoredInstance().getSeries().getStudy().getRejectionState();
            if (rejectionState == RejectionState.PARTIAL)
                return isSchedulerDeletedExpiredStudies ? RJ_SCH_FEW : RJ_PARTIAL;

            return isSchedulerDeletedExpiredStudies ? PRMDLT_SCH : RJ_COMPLET;
        }

        static EventType forStudyDeleted(StudyDeleteContext ctx) {
            return ctx.getHttpServletRequestInfo() != null ? RJ_COMPLET : PRMDLT_SCH;
        }

        static EventType forExternalRejection(RejectionNoteSent rejectionNoteSent) {
            return rejectionNoteSent.isStudyDeleted() ? RJ_COMPLET : RJ_PARTIAL;
        }

        static EventType forPatientRecord(PatientMgtContext ctx) {
            if (!ctx.getPatientVerificationStatus().equals(Patient.VerificationStatus.UNVERIFIED))
                return forPatientRecordVerification(ctx);

            String eventActionCode = ctx.getEventActionCode();
            HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
            switch (eventActionCode) {
                case "C":
                    return PAT_CREATE;
                case "U":
                    return PAT_UPDATE;
                case "D":
                    return httpServletRequestInfo == null ? PAT_DLT_SC : PAT_DELETE;
                default:
                    return null;
            }
        }

        private static EventType forPatientRecordVerification(PatientMgtContext ctx) {
            String eventActionCode = ctx.getEventActionCode();
            HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
            return eventActionCode.equals(AuditMessages.EventActionCode.Create)
                    ? PAT_CREATE
                    : httpServletRequestInfo == null
                        ? PAT_UPD_SC : PAT_UPDATE;
        }

        static EventType forHL7OutgoingPatientRecord(HL7ConnectionEvent hl7ConnEvent) {
            String messageType = hl7ConnEvent.getHL7Message().msh().getMessageType();
            return messageType.equals(CREATE_PATIENT)
                    || messageType.equals(OBSERVATION_REPORTING)
                    || messageType.equals(IMAGING_ORDER)
                        ? PAT_CREATE
                        : PAT_UPDATE;
        }

        static EventType forHL7IncomingPatientRecord(HL7ConnectionEvent hl7ConnEvent) {
            String messageType = hl7ConnEvent.getHL7Message().msh().getMessageType();
            if (messageType.startsWith(APPOINTMENTS))
                return PAT___READ;

            UnparsedHL7Message hl7ResponseMessage = hl7ConnEvent.getHL7ResponseMessage();
            if (hl7ResponseMessage instanceof ArchiveHL7Message)
                return ((ArchiveHL7Message) hl7ResponseMessage).getPatRecEventActionCode().equals(AuditMessages.EventActionCode.Create)
                        ? PAT_CREATE
                        : PAT_UPDATE;

            //HL7 Exception in HL7 Connection Event != null
            return PAT_UPDATE;
        }

        static EventType forProcedure(ProcedureContext ctx) {
            String eventActionCode = ctx.getEventActionCode();
            HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpRequest();
            return eventActionCode == null
                    ? httpServletRequestInfo == null
                        ? null
                        : PROC_STD_U
                    : eventActionCode.equals(AuditMessages.EventActionCode.Create)
                        ? PROC_STD_C
                        : eventActionCode.equals(AuditMessages.EventActionCode.Update)
                            ? PROC_STD_U
                            : PROC_STD_D;
        }

        static EventType forHL7IncomingOrderMsg(HL7ConnectionEvent hl7ConnEvent) {
            UnparsedHL7Message hl7ResponseMessage = hl7ConnEvent.getHL7ResponseMessage();
            if (hl7ResponseMessage instanceof ArchiveHL7Message)
                return ((ArchiveHL7Message) hl7ResponseMessage).getProcRecEventActionCode().equals(AuditMessages.EventActionCode.Create)
                        ? PROC_STD_C
                        : PROC_STD_U;

            //HL7 Exception in HL7 Connection Event != null
            return PROC_STD_U;
        }

        static EventType forHL7OutgoingOrderMsg(HL7Message hl7Message) {
            HL7Segment orc = hl7Message.getSegment(ORC_SEGMENT);
            String orderCtrlStatus = orc.getField(ORC_SEGMENT_ORDER_CONTROL, null)
                                        + "_"
                                        + orc.getField(ORC_SEGMENT_ORDER_STATUS, null);
            return Arrays.asList(SPS_SCHEDULED).contains(orderCtrlStatus)
                    ? PROC_STD_C
                    : PROC_STD_U;
        }

        static EventType forTaskEvent(TaskOperation operation) {
            return operation == TaskOperation.CancelTasks
                    ? CANCEL_TSK
                    : operation == TaskOperation.RescheduleTasks
                        ? RESCHD_TSK : DELETE_TSK;
        }

        @Override
        public String toString() {
            return "Audit Event Type[EventClass = " + eventClass
                    + ", EventID = " + eventID
                    + ", EventActionCode = " + eventActionCode
                    + ", RoleIDSource = " + source
                    + ", RoleIDDestination = " + destination
                    + ", EventTypeCode = " + eventTypeCode
                    + "]";
        }
    }

    static AuditMessages.EventTypeCode errorEventTypeCode(String errorCode) {
        switch (errorCode) {
            case "0":
                break;
            case "x0110":
                return AuditMessages.EventTypeCode.x0110;
            case "x0118":
                return AuditMessages.EventTypeCode.x0118;
            case "x0122":
                return AuditMessages.EventTypeCode.x0122;
            case "x0124":
                return AuditMessages.EventTypeCode.x0124;
            case "x0211":
                return AuditMessages.EventTypeCode.x0211;
            case "x0212":
                return AuditMessages.EventTypeCode.x0212;
            case "A700":
                return AuditMessages.EventTypeCode.A700;
            case "A701":
                return AuditMessages.EventTypeCode.A701;
            case "A702":
                return AuditMessages.EventTypeCode.A702;
            case "A770":
                return AuditMessages.EventTypeCode.A770;
            case "A771":
                return AuditMessages.EventTypeCode.A771;
            case "A772":
                return AuditMessages.EventTypeCode.A772;
            case "A773":
                return AuditMessages.EventTypeCode.A773;
            case "A774":
                return AuditMessages.EventTypeCode.A774;
            case "A775":
                return AuditMessages.EventTypeCode.A775;
            case "A776":
                return AuditMessages.EventTypeCode.A776;
            case "A777":
                return AuditMessages.EventTypeCode.A777;
            case "A778":
                return AuditMessages.EventTypeCode.A778;
            case "A779":
                return AuditMessages.EventTypeCode.A779;
            case "A801":
                return AuditMessages.EventTypeCode.A801;
            case "A900":
                return AuditMessages.EventTypeCode.A900;
            case "B000":
                return AuditMessages.EventTypeCode.B000;
            case "C409":
                return AuditMessages.EventTypeCode.C409;
            default:
                LOG.info("Unknown DICOM error code {}", errorCode);
        }
        return null;
    }

}
