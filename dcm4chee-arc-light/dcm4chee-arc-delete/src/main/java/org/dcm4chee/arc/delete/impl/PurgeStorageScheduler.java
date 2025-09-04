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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.delete.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.json.Json;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.ArchiveService;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2015
 */
@ApplicationScoped
public class PurgeStorageScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeStorageScheduler.class);

    @Inject
    private ArchiveService service;

    @Inject
    private DeletionServiceEJB ejb;

    @Inject
    private StoreService storeService;

    @Inject
    private StorageFactory storageFactory;

    @Inject
    private Event<StudyDeleteContext> studyDeletedEvent;

    private Set<String> inProcess = Collections.synchronizedSet(new HashSet<>());

    protected PurgeStorageScheduler() {
        super(Mode.scheduleAtFixedRate);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getPurgeStoragePollingInterval();
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        for (StorageDescriptor desc : arcDev.getStorageDescriptors()) {
            if (arcDev.getPurgeStoragePollingInterval() == null) return;
            if (!desc.isReadOnly() && inProcess.add(desc.getStorageID()))
                device.execute(() -> {
                    LOG.info("Start deletion on {}", desc);
                    try {
                        process(arcDev, desc);
                    } catch (Throwable e) {
                        LOG.warn("Deletion on {} throws:\n", desc, e);
                    } finally {
                        inProcess.remove(desc.getStorageID());
                        LOG.info("Finished deletion on {}", desc);
                    }
                });
        }
    }

    public void onExport(@Observes ExportContext ctx) {
        ExporterDescriptor desc = ctx.getExporter().getExporterDescriptor();
        String storageID = desc.getDeleteStudyFromStorageID();
        if (ctx.getException() != null || storageID == null
                || ctx.getOutcome().getStatus() != Task.Status.COMPLETED)
            return;

        String suid = ctx.getStudyInstanceUID();
        if (ctx.getSeriesInstanceUID() != null) {
            if (ctx.getSopInstanceUID() != null)
                LOG.info("Suppress deletion of objects from {} on export of Instance[uid={}] of Series[uid={}] of " +
                                "Study[uid={}] by Exporter[id={}]",
                        storageID, ctx.getSopInstanceUID(), ctx.getSeriesInstanceUID(), suid, desc.getExporterID());
            else
                LOG.info("Suppress deletion of objects from {} on export of Series[uid={}] of " +
                                "Study[uid={}] by Exporter[id={}]",
                        storageID, ctx.getSeriesInstanceUID(), suid, desc.getExporterID());
            return;
        }

        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        StorageDescriptor storageDesc = arcDev.getStorageDescriptorNotNull(storageID);
        List<String> storageIDs = arcDev.getStorageIDsOfStorageClusterForDeletion(storageDesc);
        deleteObjectsOfStudy(storageDesc, new Study.PKUID(ejb.pkByStudyIUID(suid), suid), storageIDs);
    }

    private void process(ArchiveDeviceExtension arcDev, StorageDescriptor desc) {
        deleteSeriesMetadata(arcDev, desc);
        deleteObjectsFromStorage(arcDev, desc);
        if (desc.getStorageDuration() == StorageDuration.PERMANENT)
            return;

        while (desc.hasRetentionPeriods()
                && arcDev.getPurgeStoragePollingInterval() != null
                && deleteStudies(arcDev, desc, true) > 0) {
            deleteObjectsFromStorage(arcDev, desc);
        }

        if (desc.hasDeleterThresholds() || desc.hasDeleterThresholdMaxUsableSpace()) {
            Calendar cal = Calendar.getInstance();
            long minUsableSpace = desc.getDeleterThresholdMinUsableSpace(cal);
            long maxUsableSpace = desc.getDeleterThresholdMaxUsableSpace(cal);
            long usableSpaceUnderflow = usableSpaceUnderflow(desc, minUsableSpace);
            long usedSpaceOverflow = usedSpaceOverflow(desc, maxUsableSpace);
            if (usableSpaceUnderflow == 0L && usedSpaceOverflow == 0L)
                return;

            if (usableSpaceUnderflow > usedSpaceOverflow)
                LOG.info("Usable Space on {} {} below {} - start deleting {}", desc.getStorageDuration(), desc,
                        BinaryPrefix.formatDecimal(minUsableSpace), BinaryPrefix.formatDecimal(usableSpaceUnderflow));
            else
                LOG.info("Used Space on {} {} above {} - start deleting {}", desc.getStorageDuration(), desc,
                        BinaryPrefix.formatDecimal(maxUsableSpace), BinaryPrefix.formatDecimal(usedSpaceOverflow));
            while (arcDev.getPurgeStoragePollingInterval() != null
                    && (usableSpaceUnderflow > 0L || usedSpaceOverflow > 0L)
                    && deleteStudies(arcDev, desc, false) > 0) {
                usedSpaceOverflow -= deleteObjectsFromStorage(arcDev, desc);
                usableSpaceUnderflow = usableSpaceUnderflow(desc, minUsableSpace);
            }
        } else if (!desc.hasRetentionPeriods() && desc.isNoDeletionConstraint()) {
            LOG.info("Start deleting objects from {} {}", desc.getStorageDuration(), desc);
            while (arcDev.getPurgeStoragePollingInterval() != null
                    && deleteStudies(arcDev, desc, false) > 0) {
                deleteObjectsFromStorage(arcDev, desc);
            }
        }
    }

    private long usableSpaceUnderflow(StorageDescriptor desc, long minUsableSpace) {
        if (minUsableSpace < 0L)
            return 0L;

        try (Storage storage = storageFactory.getStorage(desc)) {
            return Math.max(0L, minUsableSpace - storage.getUsableSpace());
        } catch (IOException e) {
            LOG.warn("Failed to determine usable space on {}", desc, e);
            return 0;
        }
    }

    private long usedSpaceOverflow(StorageDescriptor desc, long maxUsedSpace) {
        if (maxUsedSpace <= 0L)
            return 0L;

        String deleterThresholdBlocksFilePath = desc.getDeleterThresholdBlocksFilePath();
        if (deleterThresholdBlocksFilePath == null) {
            LOG.info("Deleter Threshold Blocks File Path on {} not configured - ignore ", desc);
            return 0L;
        }

        try {
            long blocks = desc.parseDeleterThresholdBlocksFile();
            if (blocks == 0L) {
                LOG.warn("Failed to parse Deleter Threshold Blocks File {} for {} - ignore",
                        deleterThresholdBlocksFilePath, desc);
                return 0L;
            }
            return Math.max(0, blocks * 1024 - maxUsedSpace);
        } catch (IOException e) {
            LOG.warn("Failed to read Deleter Threshold Blocks File {} for {} - ignore",
                    deleterThresholdBlocksFilePath, desc, e);
            return 0L;
        }
    }

    private int deleteStudies(ArchiveDeviceExtension arcDev, StorageDescriptor desc, boolean retentionPeriods) {
        List<String> studyStorageIDs = desc.getStudyStorageIDs(
                arcDev.getOtherStorageIDsOfStorageCluster(desc),
                Collections.emptyList(),
                null,
                true);
        Date maxAccessTime = null;
        Date preserveAccessTime =  arcDev.getPreserveStudyInterval() != null
                ? new Date(System.currentTimeMillis() - arcDev.getPreserveStudyInterval().getSeconds() * 1000L)
                : null;
        Duration deleteStudyInterval = arcDev.getDeleteStudyInterval();
        if (deleteStudyInterval != null) {
            Date minAccessTime = desc.getDeleterMinStudyAccessTime();
            if (minAccessTime == null) {
                minAccessTime = ejb.minAccessTime(arcDev, desc, studyStorageIDs, retentionPeriods);
                if (minAccessTime == null) {
                    LOG.warn("No studies for deletion found on {}", desc);
                    return 0;
                }
                service.tryReload();
                desc.setDeleterMinStudyAccessTime(minAccessTime);
                storeService.updateDeviceConfiguration(arcDev);
            }
            maxAccessTime = maxAccessTime(minAccessTime, deleteStudyInterval, preserveAccessTime);
        } else if (arcDev.getPreserveStudyInterval() != null) {
            maxAccessTime = new Date(System.currentTimeMillis() - arcDev.getPreserveStudyInterval().getSeconds() * 1000);
        }
        List<Study.PKUID> studyPks;
        for (;;) {
            try {
                studyPks = findStudiesForDeletion(arcDev, desc, studyStorageIDs, retentionPeriods, maxAccessTime);
            } catch (Exception e) {
                LOG.warn("Query for studies for deletion on {} failed", desc, e);
                return 0;
            }
            if (!studyPks.isEmpty()) {
                return desc.getStorageDuration() == StorageDuration.CACHE
                        ? deleteObjectsOfStudies(arcDev, desc, studyPks)
                        : deleteStudiesFromDB(arcDev, desc, studyPks);
            }
            service.tryReload();
            desc.setDeleterMinStudyAccessTime(
                    maxAccessTime != null ? maxAccessTime : new Date(System.currentTimeMillis()));
            storeService.updateDeviceConfiguration(arcDev);
            if (maxAccessTime == null || maxAccessTime == preserveAccessTime) break;
            LOG.info("No studies for deletion found on {} with access time before {}", desc, maxAccessTime);
            maxAccessTime = maxAccessTime(maxAccessTime, deleteStudyInterval, preserveAccessTime);
        }
        LOG.warn("No studies for deletion found on {}", desc);
        return 0;
    }

    private static Date maxAccessTime(Date minAccessTime, Duration deleteStudyInterval, Date preserveAccessTime) {
        long maxAccessTime = minAccessTime.getTime() + deleteStudyInterval.getSeconds() * 1000L;
        if (preserveAccessTime != null && maxAccessTime >= preserveAccessTime.getTime())
            return preserveAccessTime;
        return (preserveAccessTime != null && maxAccessTime >= preserveAccessTime.getTime())
                ? preserveAccessTime
                : maxAccessTime < System.currentTimeMillis()
                ? new Date(maxAccessTime)
                : null;
    }

    private List<Study.PKUID> findStudiesForDeletion(
            ArchiveDeviceExtension arcDev,
            StorageDescriptor desc,
            List<String> studyStorageIDs,
            boolean retentionPeriods,
            Date maxAccessTime) {
        List<Study.PKUID> studyPks = ejb.findStudiesForDeletionOnStorage(
                arcDev, desc, studyStorageIDs, retentionPeriods, maxAccessTime);
        String storageID = desc.getStorageID();
        String[] exportStorageID = desc.getExportStorageID();
        StoreSession storeSession = storeService.newStoreSession(device.getApplicationEntities().iterator().next());
        Duration purgeInstanceRecordsDelay = arcDev.getPurgeInstanceRecordsDelay();
        for (Iterator<Study.PKUID> iter = studyPks.iterator(); iter.hasNext();) {
            Study.PKUID studyPkUID = iter.next();
            if (exportStorageID.length == 0) {
                try {
                    storeService.restoreInstances(
                            storeSession, studyPkUID.uid, null, purgeInstanceRecordsDelay, null);
                } catch (Exception e) {
                    LOG.warn("Failed to restore Instance records of {} - defer deletion of Study from {}\n",
                            studyPkUID, desc, e);
                    ejb.updateStudyAccessTime(studyPkUID.pk);
                    iter.remove();
                }
            } else {
                int notStoredOnOtherStorage = ejb.instancesNotStoredOnExportStorage(studyPkUID.pk, desc);
                Map<String,Storage> storageMap = new HashMap<>();
                List<Series> seriesWithPurgedInstances = null;
                try {
                    seriesWithPurgedInstances = ejb.findSeriesWithPurgedInstances(studyPkUID.pk);
                    for (Series series : seriesWithPurgedInstances) {
                        Storage storage = getStorage(arcDev, series.getMetadata().getStorageID(), storageMap);
                        ReadContext readContext = storage.createReadContext();
                        readContext.setStoragePath(series.getMetadata().getStoragePath());
                        notStoredOnOtherStorage += instancesNotStoredOnOtherStorage(
                                readContext, storageID, exportStorageID);
                    }
                } finally {
                    for (Storage storage : storageMap.values()) {
                        SafeClose.close(storage);
                    }
                }
                if (notStoredOnOtherStorage > 0) {
                    LOG.info("{} instances of {} on {} not stored on Storage[id={}] - defer deletion of objects",
                            notStoredOnOtherStorage, studyPkUID, desc, exportStorageID);
                    ejb.updateStudyAccessTime(studyPkUID.pk);
                    iter.remove();
                } else if (!seriesWithPurgedInstances.isEmpty()){
                    for (Series series : seriesWithPurgedInstances) {
                        try {
                            storeService.restoreInstances(storeSession.withObjectStorageID(storageID),
                                    studyPkUID.uid,
                                    series.getSeriesInstanceUID(),
                                    purgeInstanceRecordsDelay, null);
                        } catch (Exception e) {
                            LOG.warn("Failed to restore Instance records of Series[pk={}] - defer deletion of objects from Storage[id={}]\n",
                                    series.getPk(), desc, e);
                            ejb.updateStudyAccessTime(studyPkUID.pk);
                            iter.remove();
                            break;
                        }
                    }
                }
            }
        }
        return studyPks;
    }

    private Storage getStorage(ArchiveDeviceExtension arcDev, String storageID, Map<String,Storage> storageMap) {
        Storage storage = storageMap.get(storageID);
        if (storage == null) {
            storageMap.put(storageID,
                    storage = storageFactory.getStorage(arcDev.getStorageDescriptorNotNull(storageID)));
        }
        return storage;
    }

    private static int instancesNotStoredOnOtherStorage(ReadContext ctx, String storageID, String[] exportStorageID) {
        int count = 0;
        LOG.debug("Read Metadata {} from {}", ctx.getStoragePath(), ctx.getStorage().getStorageDescriptor());
        try (InputStream in = ctx.getStorage().openInputStream(ctx)) {
            ZipInputStream zip = new ZipInputStream(in);
            while (zip.getNextEntry() != null) {
                JSONReader jsonReader = new JSONReader(Json.createParser(
                        new InputStreamReader(zip, "UTF-8")));
                Attributes metadata = jsonReader.readDataset(null);
                if (containsStorageID(metadata, PurgeStorageScheduler::matchStorageID, storageID)
                        && !containsStorageID(metadata, PurgeStorageScheduler::matchStorageIDAndCheckStatus, exportStorageID))
                    count++;
                zip.closeEntry();
            }
        } catch (Exception e) {
            LOG.error("Failed to read Metadata {} from {}",
                    ctx.getStoragePath(), ctx.getStorage().getStorageDescriptor());
            count++;
        }
        return count;
    }

    private static boolean containsStorageID(Attributes attrs, BiPredicate<Attributes, String[]> predicate,
                                             String... storageID) {
        if (predicate.test(attrs, storageID))
            return true;

        Sequence otherStorageSeq = attrs.getSequence(PrivateTag.PrivateCreator, PrivateTag.OtherStorageSequence);
        if (otherStorageSeq != null)
            for (Attributes otherStorageItem : otherStorageSeq)
                if (predicate.test(otherStorageItem, storageID))
                    return true;

        return false;
    }

    private static boolean matchStorageID(Attributes attrs, String... storageID) {
        return Arrays.asList(storageID).contains(attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StorageID));
    }

    private static boolean matchStorageIDAndCheckStatus(Attributes attrs, String... storageID) {
        if (!matchStorageID(attrs, storageID))
            return false;

        String status = attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StorageObjectStatus);
        return status == null || status.equals(LocationStatus.OK.name());
    }

    private int deleteStudiesFromDB(ArchiveDeviceExtension arcDev, StorageDescriptor desc,
                                    List<Study.PKUID> studyPkUIDs) {
        int removed = 0;
        for (Study.PKUID pkUID : studyPkUIDs) {
            if (arcDev.getPurgeStoragePollingInterval() == null)
                break;
            StudyDeleteContextImpl ctx = new StudyDeleteContextImpl(pkUID.pk);
            try {
                int limit = arcDev.getDeleteStudyChunkSize();
                int n;
                while ((n = ejb.deleteStudy(ctx, limit, false).size()) > 0) {
                    LOG.debug("Deleted {} instances of Study[pk={}]", n, pkUID.pk);
                }
                removed++;
                LOG.info("Successfully delete {} on {}", ctx.getStudy(), desc);
            } catch (Exception e) {
                LOG.warn("Failed to delete {} on {}", pkUID, desc, e);
                ctx.setException(e);
            } finally {
                if (ctx.getStudy() != null)
                    try {
                        studyDeletedEvent.fire(ctx);
                    } catch (Exception e) {
                        LOG.warn("Unexpected exception in Study Deletion audit : " + e.getMessage());
                    }
            }
        }
        return removed;
    }

    private int deleteObjectsOfStudies(ArchiveDeviceExtension arcDev, StorageDescriptor desc,
                                       List<Study.PKUID> studyPkUIDs) {
        List<String> storageIDs = arcDev.getStorageIDsOfStorageClusterForDeletion(desc);
        int removed = 0;
        for (Study.PKUID studyPkUID : studyPkUIDs) {
            if (arcDev.getPurgeStoragePollingInterval() == null)
                break;
            if (deleteObjectsOfStudy(desc, studyPkUID, storageIDs))
                removed++;
        }
        return removed;
    }

    private boolean deleteObjectsOfStudy(StorageDescriptor desc, Study.PKUID studyPkUID, List<String> storageIDs) {
        String[] prevStorageIDs = null;
        try {
            prevStorageIDs = ejb.claimDeleteStudy(studyPkUID, desc, storageIDs);
            if (prevStorageIDs == null) {
                return false;
            }
            if (ejb.deleteObjectsOfStudy(studyPkUID, desc, storageIDs)) {
                LOG.info("Successfully marked objects of {} at {} for deletion", studyPkUID, desc);
                return true;
            }
        } catch (Exception e) {
            if (prevStorageIDs == null) {
                LOG.warn("Failed to claim {} at {} for deletion", studyPkUID, desc, e);
            } else if (ejb.hasObjectsOnStorage(studyPkUID.pk, desc)) {
                LOG.warn("Failed to mark objects of {} at {} for deletion", studyPkUID, desc, e);
                ejb.setStorageIDs(studyPkUID.pk, StringUtils.concat(prevStorageIDs, '\\'));
            } else {
                LOG.info("{} does not contain objects at {}", studyPkUID, desc);
                ejb.setStorageIDs(studyPkUID.pk, Stream.of(prevStorageIDs)
                        .filter(s -> !s.equals(desc.getStorageID()))
                        .collect(Collectors.joining("\\")));
            }
        }
        return false;
    }

    private void deleteSeriesMetadata(ArchiveDeviceExtension arcDev, StorageDescriptor desc) {
        List<Metadata> metadataList;
        int fetchSize = arcDev.getPurgeStorageFetchSize();
        do {
            if (arcDev.getPurgeStoragePollingInterval() == null) return;
            LOG.debug("Query for Metadata marked for deletion from {}", desc);
            metadataList = ejb.findMetadataWithStatus(desc.getStorageID(), Metadata.Status.TO_DELETE, fetchSize);
            if (metadataList.isEmpty()) {
                LOG.debug("No Metadata marked for deletion found at {}", desc);
                break;
            }
            LOG.info("Start deleting {} Metadata from {}", metadataList.size(), desc);
            AtomicInteger success = new AtomicInteger();
            AtomicInteger skipped = new AtomicInteger();
            int deleteThreads = desc.getDeleterThreads();
            Semaphore semaphore = deleteThreads > 1 ? new Semaphore(deleteThreads) : null;
            try (Storage storage = storageFactory.getStorage(desc)) {
                for (Metadata metadata : metadataList) {
                    if (semaphore == null) {
                        deleteSeriesMetadata(storage, metadata, success, skipped);
                    } else {
                        semaphore.acquire();
                        device.execute(() -> {
                            try {
                                deleteSeriesMetadata(storage, metadata, success, skipped);
                            } finally {
                                semaphore.release();
                            }
                        });
                    }
                }
                if (semaphore != null) {
                    LOG.debug("Waiting for finishing deleting {} Metadata from {}", metadataList.size(), desc);
                    semaphore.acquire(deleteThreads);
                    semaphore.release(deleteThreads);
                }
            } catch (Exception e) {
                LOG.warn("Failed to access {}", desc, e);
            } finally {
                LOG.info("Finished deleting {} (skipped={}, failed={}) Metadata from {}",
                        success, skipped, metadataList.size() - success.get() - skipped.get(), desc);
            }
        } while (metadataList.size() == fetchSize);
    }

    private void deleteSeriesMetadata(Storage storage, Metadata metadata, AtomicInteger success,
                                      AtomicInteger skipped) {
        try {
            if (ejb.claimDeleteMetadata(metadata)) {
                storage.deleteObject(metadata.getStoragePath());
                ejb.removeMetadata(metadata);
                LOG.debug("Successfully delete {} from {}", metadata, storage);
                success.getAndIncrement();
            } else {
                skipped.getAndIncrement();
            }
        } catch (Exception e) {
            LOG.warn("Failed to delete {} from {}", metadata, storage, e);
        }
    }

    private long deleteObjectsFromStorage(ArchiveDeviceExtension arcDev, StorageDescriptor desc) {
        List<Location> locations;
        int fetchSize = arcDev.getPurgeStorageFetchSize();
        long sizeDeleted = 0;
        do {
            if (arcDev.getPurgeStoragePollingInterval() == null) return sizeDeleted;
            LOG.debug("Query for objects marked for deletion at {}", desc);
            locations = ejb.findLocationsWithStatus(desc.getStorageID(), LocationStatus.TO_DELETE, fetchSize);
            if (locations.isEmpty()) {
                LOG.debug("No objects marked for deletion found at {}", desc);
                break;
            }

            LOG.info("Start deleting {} objects from {}", locations.size(), desc);
            int deleteThreads = desc.getDeleterThreads();
            Semaphore semaphore = deleteThreads > 1 ? new Semaphore(deleteThreads) : null;
            AtomicInteger success = new AtomicInteger();
            AtomicInteger skipped = new AtomicInteger();
            try (Storage storage = storageFactory.getStorage(desc)) {
                for (Location location : locations) {
                    sizeDeleted += location.getSize();
                    if (semaphore == null) {
                        deleteLocation(storage, location, success, skipped);
                    } else {
                        semaphore.acquire();
                        device.execute(() -> {
                            try {
                                deleteLocation(storage, location, success, skipped);
                            } finally {
                                semaphore.release();
                            }
                        });
                    }
                }
                if (semaphore != null) {
                    LOG.debug("Waiting for finishing deleting {} objects from {}", locations.size(), desc);
                    semaphore.acquire(deleteThreads);
                    semaphore.release(deleteThreads);
                }
            } catch (Exception e) {
                LOG.warn("Failed to access {}", desc, e);
            } finally {
                LOG.info("Finished deleting {} (skipped={}, failed={}) objects from {}",
                        success, skipped, locations.size() - success.get() - skipped.get(), desc);
            }
        } while (locations.size() == fetchSize);
        LOG.info("Finished deleting {} from {}", BinaryPrefix.formatDecimal(sizeDeleted), desc);
        return sizeDeleted;
    }

    private void deleteLocation(Storage storage, Location location, AtomicInteger success, AtomicInteger skipped) {
        String storagePath = location.getStoragePath();
        if (storage.getStorageDescriptor().isArchiveSeriesAsTAR()) {
            int endTarPath = storagePath.indexOf('!');
            if (endTarPath > 0) storagePath = storagePath.substring(0, endTarPath);
        }
        try {
            if (ejb.claimDeleteObject(location)) {
                storage.deleteObject(storagePath);
                ejb.removeLocation(location);
                LOG.debug("Successfully delete {} from {}", storagePath, storage);
                success.getAndIncrement();
            } else {
                skipped.getAndIncrement();
            }
        } catch (Exception e) {
            LOG.warn("Failed to delete {} from {}", location, storage, e);
        }
    }

}
