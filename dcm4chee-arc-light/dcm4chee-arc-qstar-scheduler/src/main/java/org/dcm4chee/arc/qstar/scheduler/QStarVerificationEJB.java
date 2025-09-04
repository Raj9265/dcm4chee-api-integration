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

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.dcm4chee.arc.conf.LocationStatus;
import org.dcm4chee.arc.entity.Location;

import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jul 2023
 */
@Stateless
public class QStarVerificationEJB {
    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    public List<Location.LocationWithUIDs> findByLocationsWithStatusCreatedBefore(
            String storageID, LocationStatus status, Date before, int limit) {
        return em.createNamedQuery(Location.FIND_BY_STATUS_CREATED_BEFORE, Location.LocationWithUIDs.class)
                .setParameter(1, storageID)
                .setParameter(2, status)
                .setParameter(3, before)
                .setMaxResults(limit)
                .getResultList();
    }

    public int setLocationStatus(Long pk, LocationStatus status) {
        return em.createNamedQuery(Location.SET_STATUS)
                .setParameter(1, pk)
                .setParameter(2, status)
                .executeUpdate();
    }

    public int setLocationStatusByMultiRef(Integer multiRef, LocationStatus status) {
        return em.createNamedQuery(Location.SET_STATUS_BY_MULTI_REF)
                .setParameter(1, multiRef)
                .setParameter(2, status)
                .executeUpdate();
    }
}
