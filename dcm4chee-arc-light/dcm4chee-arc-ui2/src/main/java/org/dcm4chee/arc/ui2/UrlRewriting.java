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

package org.dcm4chee.arc.ui2;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.IOException;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2022
 */
@WebFilter(asyncSupported = true, urlPatterns = {
        "/de/statistics/*",
        "/de/dicom-route",
        "/de/workflow-management",
        "/de/xds",
        "/de/lifecycle-management",
        "/de/monitoring/*",
        "/de/correct-data/*",
        "/de/audit-record-repository/*",
        "/de/migration/*",
        "/de/agfa-migration/*",
        "/de/study/*",
        "/de/permission-denied",
        "/de/device/*",
        "/en/configuration/*",
        "/en/statistics/*",
        "/en/dicom-route",
        "/en/workflow-management",
        "/en/xds",
        "/en/lifecycle-management",
        "/en/monitoring/*",
        "/en/correct-data/*",
        "/en/audit-record-repository/*",
        "/en/migration/*",
        "/en/agfa-migration/*",
        "/en/study/*",
        "/en/permission-denied",
        "/en/device/*",
        "/en/configuration/*",
        "/es/statistics/*",
        "/es/dicom-route",
        "/es/workflow-management",
        "/es/xds",
        "/es/lifecycle-management",
        "/es/monitoring/*",
        "/es/correct-data/*",
        "/es/audit-record-repository/*",
        "/es/migration/*",
        "/es/agfa-migration/*",
        "/es/study/*",
        "/es/permission-denied",
        "/es/device/*",
        "/es/configuration/*",
        "/hi/statistics/*",
        "/hi/dicom-route",
        "/hi/workflow-management",
        "/hi/xds",
        "/hi/lifecycle-management",
        "/hi/monitoring/*",
        "/hi/correct-data/*",
        "/hi/audit-record-repository/*",
        "/hi/migration/*",
        "/hi/agfa-migration/*",
        "/hi/study/*",
        "/hi/permission-denied",
        "/hi/device/*",
        "/hi/configuration/*",
        "/it/statistics/*",
        "/it/dicom-route",
        "/it/workflow-management",
        "/it/xds",
        "/it/lifecycle-management",
        "/it/monitoring/*",
        "/it/correct-data/*",
        "/it/audit-record-repository/*",
        "/it/migration/*",
        "/it/agfa-migration/*",
        "/it/study/*",
        "/it/permission-denied",
        "/it/device/*",
        "/it/configuration/*",
        "/ja/statistics/*",
        "/ja/dicom-route",
        "/ja/workflow-management",
        "/ja/xds",
        "/ja/lifecycle-management",
        "/ja/monitoring/*",
        "/ja/correct-data/*",
        "/ja/audit-record-repository/*",
        "/ja/migration/*",
        "/ja/agfa-migration/*",
        "/ja/study/*",
        "/ja/permission-denied",
        "/ja/device/*",
        "/ja/configuration/*",
        "/mr/statistics/*",
        "/mr/dicom-route",
        "/mr/workflow-management",
        "/mr/xds",
        "/mr/lifecycle-management",
        "/mr/monitoring/*",
        "/mr/correct-data/*",
        "/mr/audit-record-repository/*",
        "/mr/migration/*",
        "/mr/agfa-migration/*",
        "/mr/study/*",
        "/mr/permission-denied",
        "/mr/device/*",
        "/mr/configuration/*",
        "/pt/statistics/*",
        "/pt/dicom-route",
        "/pt/workflow-management",
        "/pt/xds",
        "/pt/lifecycle-management",
        "/pt/monitoring/*",
        "/pt/correct-data/*",
        "/pt/audit-record-repository/*",
        "/pt/migration/*",
        "/pt/agfa-migration/*",
        "/pt/study/*",
        "/pt/permission-denied",
        "/pt/device/*",
        "/pt/configuration/*",
        "/ru/statistics/*",
        "/ru/dicom-route",
        "/ru/workflow-management",
        "/ru/xds",
        "/ru/lifecycle-management",
        "/ru/monitoring/*",
        "/ru/correct-data/*",
        "/ru/audit-record-repository/*",
        "/ru/migration/*",
        "/ru/agfa-migration/*",
        "/ru/study/*",
        "/ru/permission-denied",
        "/ru/device/*",
        "/ru/configuration/*",
        "/fr/statistics/*",
        "/fr/dicom-route",
        "/fr/workflow-management",
        "/fr/xds",
        "/fr/lifecycle-management",
        "/fr/monitoring/*",
        "/fr/correct-data/*",
        "/fr/audit-record-repository/*",
        "/fr/migration/*",
        "/fr/agfa-migration/*",
        "/fr/study/*",
        "/fr/permission-denied",
        "/fr/device/*",
        "/fr/configuration/*",
        "/zh/statistics/*",
        "/zh/dicom-route",
        "/zh/workflow-management",
        "/zh/xds",
        "/zh/lifecycle-management",
        "/zh/monitoring/*",
        "/zh/correct-data/*",
        "/zh/audit-record-repository/*",
        "/zh/migration/*",
        "/zh/agfa-migration/*",
        "/zh/study/*",
        "/zh/permission-denied",
        "/zh/device/*",
        "/zh/configuration/*"
})
public class UrlRewriting implements Filter {
    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        final HttpServletRequest httpServletRequest = HttpServletRequest.class.cast(request);
        chain.doFilter(new HttpServletRequestWrapper(httpServletRequest) {
            @Override
            public String getServletPath() {
                return httpServletRequest.getServletPath().substring(0, 3) + "/index.html";
            }
        }, response);
    }
}
