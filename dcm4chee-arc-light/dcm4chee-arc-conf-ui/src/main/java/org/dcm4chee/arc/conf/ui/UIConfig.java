/*
 * ** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2017
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
 * ** BEGIN LICENSE BLOCK *****
 */

package org.dcm4chee.arc.conf.ui;

import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Shefki Esadi <shralsheki@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Nov 2017
 */
public class UIConfig {

    private String name;
    private String[] modalities = {};
    private String[] widgetAets = {};
    private String[] mwlWorklistLabels = {};
    private String[] storeAccessControlIDs  =  {};

    private String xdsUrl;
    private String backgroundUrl;
    private String dateTimeFormat;
    private boolean hideClock;



    private String institutionNameFilterType;
    private String[] institutionNames = {};
    private String[] issuerOfPatientIDSequence = {};
    private String[] issuerOfAccessionNumberSequence = {};
    private String[] issuerOfAdmissionIDSequence = {};

    private boolean hideOtherPatientIDs;
    private String pageTitle;
    private String personNameFormat;
    private String logoUrl;
    private String aboutInfo;
    private String[] defaultWidgetAets = {};
    private Map<String, UIPermission> permissions = new HashMap<>();
    private Map<String, UIDiffConfig> diffConfigs = new HashMap<>();
    private Map<String, UIDashboardConfig> dashboardConfigs = new HashMap<>();
    private Map<String, UIElasticsearchConfig> elasticsearchConfigs = new HashMap<>();
    private Map<String, UIDeviceCluster> deviceCluster = new HashMap<>();
    private Map<String, UIFiltersTemplate> filterTemplatte = new HashMap<>();
    private Map<String, UIAetList> aetList  = new HashMap<>();
    private Map<String, UICreateDialogTemplate> dialogTemplate  = new HashMap<>();
    private Map<String, UIWebAppList> webAppList  = new HashMap<>();
    private Map<String, UITenantConfig> tenantConfig  = new HashMap<>();
    private Map<String, UILanguageConfig> languageConfig = new HashMap<>();
    private Map<String, UITableConfig> tableConfig = new HashMap<>();

    public UIConfig() {
    }

    public UIConfig(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getModalities() {
        return modalities;
    }

    public void setModalities(String[] modalities) {
        this.modalities = modalities;
    }

    public String[] getWidgetAets() {
        return widgetAets;
    }

    public void setWidgetAets(String[] widgetAets) {
        this.widgetAets = widgetAets;
    }

    public String getXdsUrl() {
        return xdsUrl;
    }

    public void setXdsUrl(String xdsUrl) {
        this.xdsUrl = xdsUrl;
    }

    public String getBackgroundUrl() {
        return backgroundUrl;
    }

    public void setBackgroundUrl(String backgroundUrl) {
        this.backgroundUrl = backgroundUrl;
    }

    public String getDateTimeFormat() {
        return dateTimeFormat;
    }

    public void setDateTimeFormat(String dateTimeFormat) {
        this.dateTimeFormat = dateTimeFormat;
    }

    public boolean isHideClock() {
        return hideClock;
    }


    public String[] getInstitutionNames() {
        return institutionNames;
    }

    public String[] getIssuerOfPatientIDSequence() {
        return issuerOfPatientIDSequence;
    }

    public void setIssuerOfPatientIDSequence(String[] issuerOfPatientIDSequence) {
        this.issuerOfPatientIDSequence = issuerOfPatientIDSequence;
    }

    public String[] getIssuerOfAccessionNumberSequence() {
        return issuerOfAccessionNumberSequence;
    }

    public void setIssuerOfAccessionNumberSequence(String[] issuerOfAccessionNumberSequence) {
        this.issuerOfAccessionNumberSequence = issuerOfAccessionNumberSequence;
    }

    public String[] getIssuerOfAdmissionIDSequence() {
        return issuerOfAdmissionIDSequence;
    }

    public void setIssuerOfAdmissionIDSequence(String[] issuerOfAdmissionIDSequence) {
        this.issuerOfAdmissionIDSequence = issuerOfAdmissionIDSequence;
    }

    public void setInstitutionNames(String[] institutionNames) {
        this.institutionNames = institutionNames;
    }

    public String getInstitutionNameFilterType() {
        return institutionNameFilterType;
    }

    public void setInstitutionNameFilterType(String institutionNameFilterType) {
        this.institutionNameFilterType = institutionNameFilterType;
    }
    public boolean isHideOtherPatientIDs() {
        return hideOtherPatientIDs;
    }

    public void setHideOtherPatientIDs(boolean hideOtherPatientIDs) {
        this.hideOtherPatientIDs = hideOtherPatientIDs;
    }
    public void setHideClock(boolean hideClock) {
        this.hideClock = hideClock;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public void setPageTitle(String pageTitle) {
        this.pageTitle = pageTitle;
    }
    public String getPersonNameFormat() {
        return personNameFormat;
    }

    public void setPersonNameFormat(String personNameFormat) {
        this.personNameFormat = personNameFormat;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getAboutInfo() {
        return aboutInfo;
    }

    public void setAboutInfo(String aboutInfo) {
        this.aboutInfo = aboutInfo;
    }

    public String[] getDefaultWidgetAets() {
        return defaultWidgetAets;
    }

    public void setDefaultWidgetAets(String[] defaultWidgetAets) {
        this.defaultWidgetAets = defaultWidgetAets;
    }

    public String[] getMWLWorklistLabels() {
        return mwlWorklistLabels;
    }

    public void setMWLWorklistLabels(String[] mwlWorklistLabels) {
        this.mwlWorklistLabels = mwlWorklistLabels;
    }

    public UIPermission getPermission(String name) {
        return permissions.get(name);
    }

    public void addPermission(UIPermission permission) {
        permissions.put(permission.getName(), permission);
    }

    public UIPermission removePermission(String name) {
        return permissions.remove(name);
    }

    public Collection<UIPermission> getPermissions() {
        return permissions.values();
    }

    public UIDiffConfig getDiffConfig(String name) {
        return diffConfigs.get(name);
    }

    public void addDiffConfig(UIDiffConfig diffConfig) {
        diffConfigs.put(diffConfig.getName(), diffConfig);
    }

    public UIDiffConfig removeDiffConfig(String name) {
        return diffConfigs.remove(name);
    }

    public Collection<UIDiffConfig> getDiffConfigs() {
        return diffConfigs.values();
    }

    public UIDashboardConfig getDashboardConfig(String name) {
        return dashboardConfigs.get(name);
    }

    public void addDashboardConfig(UIDashboardConfig dashboardConfig) {
        dashboardConfigs.put(dashboardConfig.getName(), dashboardConfig);
    }

    public UIDashboardConfig removeDashboardConfig(String name) {
        return dashboardConfigs.remove(name);
    }

    public Collection<UIDashboardConfig> getDashboardConfigs() {
        return dashboardConfigs.values();
    }

    public UIElasticsearchConfig getElasticsearchConfig(String name) {
        return elasticsearchConfigs.get(name);
    }

    public void addElasticsearchConfig(UIElasticsearchConfig elasticsearchConfig) {
        elasticsearchConfigs.put(elasticsearchConfig.getName(), elasticsearchConfig);
    }

    public UIElasticsearchConfig removeElasticsearchConfig(String name) {
        return elasticsearchConfigs.remove(name);
    }

    public Collection<UIElasticsearchConfig> getElasticsearchConfigs() {
        return elasticsearchConfigs.values();
    }

    public UIDeviceCluster getDeviceCluster(String name) {
        return deviceCluster.get(name);
    }

    public void addDeviceCluster(UIDeviceCluster cluster) {
        deviceCluster.put(cluster.getClusterName(), cluster);
    }

    public UIDeviceCluster removeDeviceCluster(String name) {
        return deviceCluster.remove(name);
    }

    public Collection<UIDeviceCluster> getDeviceClusters() {
        return deviceCluster.values();
    }


    public UIFiltersTemplate getFilterTemplate(String id) {
        return filterTemplatte.get(id);
    }

    public void addFilterTemplate(UIFiltersTemplate filtersTemplate) {
        filterTemplatte.put(filtersTemplate.getFilterGroupName(), filtersTemplate);
    }

    public UIFiltersTemplate removeFilterTemplate(String name) {
        return filterTemplatte.remove(name);
    }

    public Collection<UIFiltersTemplate> getFilterTemplates() {
        return filterTemplatte.values();
    }


    public void setAetList(Map<String, UIAetList> aetList) {
        this.aetList = aetList;
    }
    public void setWebAppList(Map<String, UIWebAppList> webAppList) {
        this.webAppList = webAppList;
    }



    public UIAetList getAetList(String name) {
        return this.aetList.get(name);
    }
    public UICreateDialogTemplate getCreateDialogTemplate(String name) {
        return this.dialogTemplate.get(name);
    }

    public UIWebAppList getWebAppList(String name) {
        return this.webAppList.get(name);
    }

    public UITenantConfig getTenantConfig(String name) {
        return tenantConfig.get(name);
    }

    public void setTenantConfig(Map<String, UITenantConfig> tenantConfig) {
        this.tenantConfig = tenantConfig;
    }

    public void addAetList(UIAetList aetList) {
        this.aetList.put(aetList.getAetListName(), aetList);
    }
    public void addCreatDialogTemplate(UICreateDialogTemplate createDialogTemplate) {
        this.dialogTemplate.put(createDialogTemplate.getTemplateName(), createDialogTemplate);
    }
    public void addWebAppList(UIWebAppList webAppList) {
        this.webAppList.put(webAppList.getWebAppListName(),webAppList);
    }
    public void addTenant(UITenantConfig tenantConfig) {
        this.tenantConfig.put(tenantConfig.getTenantConfigName(),tenantConfig);
    }

    public UIAetList removeAetList(String name){
        return this.aetList.remove(name);
    }

    public UIWebAppList removeWebAppList(String name){
        return this.webAppList.remove(name);
    }

    public Collection<UIAetList> getAetLists() {
        return this.aetList.values();
    }
    public Collection<UICreateDialogTemplate> getCreateDialogTemplates() {
        return this.dialogTemplate.values();
    }

    public Collection<UIWebAppList> getWebAppLists(){
        return this.webAppList.values();
    }

    public String[] getStoreAccessControlIDs() {
        return storeAccessControlIDs;
    }

    public void setStoreAccessControlIDs(String[] storeAccessControlIDs) {
        this.storeAccessControlIDs = storeAccessControlIDs;
    }

    public Collection<UITenantConfig> getTenantConfigs() {
        return this.tenantConfig.values();
    }

    public UILanguageConfig getLanguageConfig(String name) {
        return languageConfig.get(name);
    }

    public void addLanguageConfig(UILanguageConfig language) {
        languageConfig.put(language.getName(), language);
    }

    public UILanguageConfig removeLanguageConfig(String name) {
        return languageConfig.remove(name);
    }

    public Collection<UILanguageConfig> getLanguageConfigs() {
        return languageConfig.values();
    }

    public UITableConfig getTableConfig(String name) {
        return tableConfig.get(name);
    }

    public void addTableConfig(UITableConfig table) {
        tableConfig.put(table.getName(), table);
    }

    public UITableConfig removeTableConfig(String name) {
        return tableConfig.remove(name);
    }

    public Collection<UITableConfig> getTableConfigs() {
        return tableConfig.values();
    }


}
