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
 * Portions created by the Initial Developer are Copyright (C) 2013-2019
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

package org.dcm4chee.arc.study.impl;

import jakarta.ejb.EJBException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.patient.NonUniquePatientException;
import org.dcm4chee.arc.patient.PatientMergedException;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientMismatchException;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.study.StudyMissingException;
import org.dcm4chee.arc.study.StudyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2016
 */
@ApplicationScoped
public class StudyServiceImpl implements StudyService {
    static final Logger LOG = LoggerFactory.getLogger(StudyServiceImpl.class);

    @Inject
    private Device device;

    @Inject
    private StudyServiceEJB ejb;

    @Inject
    private Event<StudyMgtContext> updateStudyEvent;

    @Override
    public StudyMgtContext createStudyMgtContextWEB(HttpServletRequestInfo httpRequest, ApplicationEntity ae) {
        return new StudyMgtContextImpl(device).withHttpRequest(httpRequest).withApplicationEntity(ae);
    }

    @Override
    public StudyMgtContext createStudyMgtContextHL7(Socket socket, Connection conn, UnparsedHL7Message msg) {
        return new StudyMgtContextImpl(device)
                .withSocket(socket)
                .withConnection(conn)
                .withUnparsedHL7Message(msg);
    }

    @Override
    public void updateStudy(StudyMgtContext ctx) throws StudyMissingException, PatientMismatchException {
        try {
            ejb.updateStudy(ctx);
        } catch (RuntimeException e) {
            ctx.setException(e);
            throw e;
        } finally {
            if (ctx.getEventActionCode() != null)
                updateStudyEvent.fire(ctx);
        }
    }

    @Override
    public void updateSeries(StudyMgtContext ctx) throws StudyMissingException, PatientMismatchException {
        try {
            ejb.updateSeries(ctx);
        } catch (RuntimeException e) {
            ctx.setException(e);
            throw e;
        } finally {
            if (ctx.getEventActionCode() != null)
                updateStudyEvent.fire(ctx);
        }
    }

    @Override
    public void updateInstance(StudyMgtContext ctx) throws StudyMissingException, PatientMismatchException {
        try {
            ejb.updateInstance(ctx);
        } catch (RuntimeException e) {
            ctx.setException(e);
            throw e;
        } finally {
            if (ctx.getEventActionCode() != null)
                updateStudyEvent.fire(ctx);
        }
    }

    @Override
    public void updateStudyRequest(StudyMgtContext ctx) throws StudyMissingException {
        try {
            ejb.updateStudyRequest(ctx);
        } catch (RuntimeException e) {
            ctx.setException(e);
            throw e;
        } finally {
            if (ctx.getEventActionCode() != null)
                updateStudyEvent.fire(ctx);
        }
    }

    @Override
    public void updateSeriesRequest(StudyMgtContext ctx) throws StudyMissingException {
        try {
            ejb.updateSeriesRequest(ctx);
        } catch (RuntimeException e) {
            ctx.setException(e);
            throw e;
        } finally {
            if (ctx.getEventActionCode() != null)
                updateStudyEvent.fire(ctx);
        }
    }

    @Override
    public void updateExpirationDate(StudyMgtContext ctx) throws StudyMissingException {
        try {
            ejb.updateExpirationDate(ctx);
        } catch (Exception e) {
            ctx.setException(e);
            throw e;
        } finally {
            if (ctx.getEventActionCode() != null)
                updateStudyEvent.fire(ctx);
        }
    }

    @Override
    public void updateAccessControlID(StudyMgtContext ctx) throws StudyMissingException {
        try {
            ejb.updateAccessControlID(ctx);
        } catch (RuntimeException e) {
            ctx.setException(e);
            throw e;
        } finally {
            if (ctx.getEventActionCode() != null)
                updateStudyEvent.fire(ctx);
        }
    }

    @Override
    public void moveStudyToPatient(String studyUID, PatientMgtContext ctx)
            throws StudyMissingException, NonUniquePatientException, PatientMergedException {
        ArchiveDeviceExtension arcDev = ctx.getArchiveAEExtension().getArchiveDeviceExtension();
        int retries = arcDev.getStoreUpdateDBMaxRetries();
        for (;;) {
            try {
                ejb.moveStudyToPatient(studyUID, ctx);
                return;
            } catch (EJBException e) {
                ctx.setException(e);
                if (retries-- > 0) {
                    LOG.info("{}: Failed to update DB caused by {} - retry", ctx.getHttpServletRequestInfo(),
                            DicomServiceException.initialCauseOf(e).toString());
                    LOG.debug("{}: Failed to update DB - retry:\n", ctx.getHttpServletRequestInfo(), e);
                } else {
                    LOG.warn("{}: Failed to update DB:\n", ctx.getHttpServletRequestInfo(), e);
                    throw e;
                }
            }
            try {
                Thread.sleep(arcDev.storeUpdateDBRetryDelay());
            } catch (InterruptedException e) {
                LOG.info("{}: Failed to delay retry to update DB:\n", ctx.getHttpServletRequestInfo(), e);
            }
        }
    }

}
