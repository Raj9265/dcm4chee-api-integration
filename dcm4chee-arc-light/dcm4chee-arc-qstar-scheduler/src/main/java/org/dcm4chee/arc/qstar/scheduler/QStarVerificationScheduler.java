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
 * Portions created by the Initial Developer are Copyright (C) 2013-2021
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

package org.dcm4chee.arc.qstar.scheduler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.dcm4che3.qstar.*;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.qstar.QStarVerification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2023
 */
@ApplicationScoped
public class QStarVerificationScheduler extends Scheduler {
    private static final Logger LOG = LoggerFactory.getLogger(QStarVerificationScheduler.class);

    @Inject
    private QStarVerificationEJB ejb;

    @Inject
    private Event<QStarVerification> event;

    protected QStarVerificationScheduler() {
        super(Scheduler.Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev != null
                && arcDev.getQStarVerificationStorageID() != null
                && (arcDev.getQStarVerificationURL() != null || arcDev.getQStarVerificationMockAccessState() != null)
                ? arcDev.getQStarVerificationPollingInterval()
                : null;
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        List<Location.LocationWithUIDs> locations;
        Map<Integer, LocationStatus> verifiedTars = new HashMap<>();
        int fetchSize = arcDev.getQStarVerificationFetchSize();
        try (QStarConnection conn = new QStarConnection(arcDev)) {
            do {
                LOG.debug("Query for objects to verify QStar Access State");
                locations = ejb.findByLocationsWithStatusCreatedBefore(
                        arcDev.getQStarVerificationStorageID(),
                        LocationStatus.VERIFY_QSTAR_ACCESS_STATE,
                        new Date(System.currentTimeMillis() - arcDev.getQStarVerificationDelay().getSeconds() * 1000L),
                        fetchSize);
                if (locations.isEmpty()) {
                    LOG.debug("No objects to verify QStar Access State found ");
                    break;
                }

                LOG.info("Start verifying QStar Access State of {} objects", locations.size());
                QStarVerifications qStarVerifications = new QStarVerifications();
                for (Location.LocationWithUIDs l : locations) {
                    StorageDescriptor storageDescriptor = arcDev.getStorageDescriptorNotNull(l.location.getStorageID());
                    String mountPath = ensureTrailingSlash(
                            storageDescriptor.getProperty("mountPath",
                                    storageDescriptor.getStorageURI().getPath()));
                    String storagePath = mountPath + l.location.getStoragePath();
                    int tarPathEnd = -1;
                    LocationStatus status;
                    if (!storageDescriptor.isArchiveSeriesAsTAR() || (tarPathEnd = storagePath.indexOf('!')) < 0) {
                        status = conn.nextLocationStatus(l.location, storagePath);
                        ejb.setLocationStatus(l.location.getPk(), status);
                    } else {
                        status = verifiedTars.get(l.location.getMultiReference());
                        if (status == null) {
                            status = conn.nextLocationStatus(l.location, storagePath.substring(0, tarPathEnd));
                            ejb.setLocationStatusByMultiRef(l.location.getMultiReference(), status);
                            verifiedTars.put(l.location.getMultiReference(), status);
                        }
                    }
                    qStarVerifications.get(status, l, storagePath, tarPathEnd).sopRefs
                            .add(new QStarVerification.SOPRef(l.sopClassUID, l.sopInstanceUID));
                }
                qStarVerifications.logAndFireEvents();
            } while (locations.size() != fetchSize
                    && arcDev.getQStarVerificationPollingInterval() != null
                    && arcDev.getQStarVerificationStorageID() != null);
        }
    }

    private static String ensureTrailingSlash(String mountPath) {
        return mountPath.isEmpty() || mountPath.charAt(mountPath.length() - 1) != '/'
                ? mountPath + '/'
                : mountPath;
    }

    private static LocationStatus toLocationStatus(int accessState) {
        switch (accessState) {
            case 0:
                return LocationStatus.QSTAR_ACCESS_STATE_NONE;
            case 1:
                return LocationStatus.QSTAR_ACCESS_STATE_EMPTY;
            case 2:
                return LocationStatus.QSTAR_ACCESS_STATE_UNSTABLE;
            case 3:
                return LocationStatus.OK;
            case 4:
                return LocationStatus.QSTAR_ACCESS_STATE_OUT_OF_CACHE;
            case 5:
                return LocationStatus.QSTAR_ACCESS_STATE_OFFLINE;
            default:
                return LocationStatus.QSTAR_ACCESS_STATE_ERROR_STATUS;
        }
    }

    private final class QStarConnection implements AutoCloseable {

        private final LocationStatus mockLocationStatus;
        private final String url;
        private final String user;
        private final String password;
        private WSWebServiceSoapPort port;
        private WSUserLoginResponse userLogin;

        public QStarConnection(ArchiveDeviceExtension arcDev) {
            this.mockLocationStatus =  arcDev.getQStarVerificationMockAccessState() != null
                    ? toLocationStatus(arcDev.getQStarVerificationMockAccessState())
                    : null;
            this.url = arcDev.getQStarVerificationURLwoUserInfo();
            this.user = arcDev.getQStarVerificationUser();
            this.password = arcDev.getQStarVerificationPassword();
        }

        public LocationStatus nextLocationStatus(Location location, String filePath) {
            if (mockLocationStatus != null) return mockLocationStatus;
            if (userLogin == null) {
                port = QStarUtils.getWSWebServiceSoapPort(url);
                userLogin = QStarUtils.login(port, user, password);
                if (userLogin.getResult() != 0) {
                    LOG.warn("Failed to authenticate QStar user: {} @ {} - result={}, resultString='{}'",
                            user, url, userLogin.getResult(), userLogin.getResultString());
                    throw new RuntimeException("Failed to authenticate user: " + user + " @ " + url);
                }
            }
            WSGetFileInfoResponse fileInfo = QStarUtils.getFileInfo(port, userLogin, filePath);
            if (fileInfo.getStatus() == 0 && fileInfo.getInfo() != null) {
                long stateAccess = fileInfo.getInfo().getStateAccess();
                if (stateAccess != 3) {
                    LOG.warn("Get QStar Access State {} for {} from {}",
                            QStarUtils.stateAccessAsString(stateAccess),
                            location, url);
                } else {
                    LOG.info("Get QStar Access State {} for {} from {}",
                            QStarUtils.stateAccessAsString(stateAccess),
                            location, url);
                }
                return toLocationStatus((int) stateAccess);
            }
            LOG.warn("Failed to get QStar Access State for {} from {} - status={}",
                    location, url, fileInfo.getStatus());
            return LocationStatus.QSTAR_ACCESS_STATE_ERROR_STATUS;
        }

        @Override
        public void close() {
            if (userLogin != null && userLogin.getResult() == 0) {
                QStarUtils.logout(port, userLogin);
            }
        }

    }

    private final class QStarVerifications {
        final EnumMap<LocationStatus, Map<Long, QStarVerification>> byStatus =
                new EnumMap<>(LocationStatus.class);

        QStarVerification get(LocationStatus status, Location.LocationWithUIDs l,
                              String storagePath, int tarPathEnd) {
            Map<Long, QStarVerification> withStatus = byStatus.get(status);
            if (withStatus == null) {
                byStatus.put(status,
                        withStatus = new HashMap<>());
            }
            QStarVerification qStarVerification = withStatus.get(l.seriesPk);
            if (qStarVerification == null) {
                withStatus.put(l.seriesPk, qStarVerification =
                        new QStarVerification(status, l.seriesPk, l.studyInstanceUID, l.seriesInstanceUID,
                                tarPathEnd < 0 ? storagePath : storagePath.substring(0, tarPathEnd)));
            }
            return qStarVerification;
        }

        void logAndFireEvents() {
            for (Map<Long, QStarVerification> withStatus : byStatus.values()) {
                for (QStarVerification qStarVerification : withStatus.values()) {
                    LOG.info("Update status of {} objects of Series[pk={}, uid={}] of Study[uid={}] stored in {} to {}",
                            qStarVerification.sopRefs.size(),
                            qStarVerification.seriesPk,
                            qStarVerification.seriesInstanceUID,
                            qStarVerification.studyInstanceUID,
                            qStarVerification.filePath,
                            qStarVerification.status);
                    try {
                        event.fire(qStarVerification);
                    } catch (Exception e) {
                        LOG.warn("Processing of notification about {} failed:\n", qStarVerification, e);
                    }
                }
            }
        }
    }
}
