package org.mokxa.plugins;

import org.joget.apps.app.dao.EnvironmentVariableDao;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.EnvironmentVariable;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.mokxa.plugins.util.TenantCalendarSyncUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class SyncTenantCalendarEventsTool extends DefaultApplicationPlugin {

    private static final String ENV_LAST_SYNC = "TENANT_CALENDAR_LAST_SYNC_UTC";

    @Override
    public String getName() {
        return "Sync Tenant Calendar Events Tool";
    }

    @Override
    public String getLabel() {
        return "Sync Tenant Outlook Calendar Events";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Background tool to sync tenant-wide Outlook calendar events into Joget using Microsoft Graph app permissions.";
    }


    @Override
    public Object execute(Map props) {

        LogUtil.info(getClass().getName(), "==== Tenant Calendar Sync START ====");

        try {
            String tenantId     = getPropertyString("tenantId");
            String clientId     = getPropertyString("clientId");
            String clientSecret = getPropertyString("clientSecret");

            String formId    = getPropertyString("formDefId");
            String tableName =  getTableName(formId);

            String extendedPropId    = getPropertyString("extendedPropId");
            String extendedPropField = getPropertyString("extendedPropField");

            String eventIdField  = getPropertyString("eventIdField");
            String eventField    = getPropertyString("eventField");
            String userField     = getPropertyString("userField");
            String durationField = getPropertyString("durationField");

            Instant nowUtc = Instant.now();
            Instant lastSyncUtc = readLastSync();

            String startUtc = formatUtc(lastSyncUtc);
            String endUtc = formatUtc(nowUtc);

            LogUtil.info(getClass().getName(), "Sync Window UTC → " + startUtc + " to " + endUtc);

            TenantCalendarSyncUtil util = new TenantCalendarSyncUtil(tenantId, clientId, clientSecret);

            if (!util.authenticate()) {
                LogUtil.warn(getClass().getName(), "Authentication failed, aborting sync");
                return null;
            }

            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

            util.getAllUsers().forEach(user -> {

                String userId = user.getString("id");
                String userEmail = user.optString("mail",
                        user.optString("userPrincipalName"));

                JSONArray events = util.getUserEvents(userId, startUtc, endUtc,buildExtendedPropId());

                for (int i = 0; i < events.length(); i++) {

                    JSONObject e = events.getJSONObject(i);

                    if (e.optBoolean("isAllDay")) continue;
                    if (e.optBoolean("isCancelled")) continue;

                    String eventId = e.getString("id");
                    String subject = e.optString("subject");

                    JSONObject start = e.getJSONObject("start");
                    JSONObject end = e.getJSONObject("end");

                    Instant startTime = Instant.parse(start.getString("dateTime") + "Z");
                    Instant endTime   = Instant.parse(end.getString("dateTime") + "Z");

                    if (endTime.isAfter(nowUtc)) {
                        continue;
                    }

                    if (endTime.isBefore(lastSyncUtc)) {
                        continue;
                    }

                    long durationSeconds = endTime.getEpochSecond() - startTime.getEpochSecond();

                    if (durationSeconds <= 0) continue;

                    String duration = formatDuration(durationSeconds);

                    String extendedValue = util.extractExtendedPropValue(e,buildExtendedPropId());

                    FormRow row = new FormRow();
                    row.setId(UUID.randomUUID().toString());
                    row.put(eventIdField, eventId);
                    row.put(eventField, subject);
                    row.put(userField, userEmail);
                    row.put(extendedPropField, extendedValue!=null?extendedValue:"");
                    row.put(durationField, duration);
                    row.setDateCreated(new Date());
                    FormRowSet rowSet = new FormRowSet();
                    rowSet.add(row);
                    formDataDao.saveOrUpdate( formId, tableName, rowSet);
                }
            });

            updateLastSync(nowUtc);

            LogUtil.info(getClass().getName(), "==== Tenant Calendar Sync END ====");

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Unexpected error during tenant calendar sync");
        }

        return null;
    }

    private String getTableName(String formId) {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
        return formDefinitionDao.loadById(formId, appDef).getTableName();
    }

    private String buildExtendedPropId() {

        String guid = getPropertyString("extendedPropGuid");
        String name = getPropertyString("extendedPropName");

        if (guid == null || guid.isEmpty()) {
            return null;
        }

        if (name == null || name.isEmpty()) {
            return null;
        }

        return "String {" + guid + "} Name " + name;
    }


    private Instant readLastSync() {

        EnvironmentVariableDao variableDao = (EnvironmentVariableDao) AppUtil.getApplicationContext().getBean("environmentVariableDao");

        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        EnvironmentVariable variable = variableDao.loadById(ENV_LAST_SYNC, appDef);

        if (variable == null || variable.getValue() == null || variable.getValue().isEmpty()) {

            Instant fallback = Instant.now().minusSeconds(86400);

            LogUtil.info(getClass().getName(), "Last sync not found, using fallback → " + fallback);

            return fallback;
        }

        try {
            return Instant.parse(variable.getValue());
        } catch (Exception e) {
            LogUtil.warn(getClass().getName(),
                    "Invalid last sync value, resetting");

            return Instant.now().minusSeconds(86400);
        }
    }

    private void updateLastSync(Instant utc) {

        EnvironmentVariableDao variableDao = (EnvironmentVariableDao) AppUtil.getApplicationContext().getBean("environmentVariableDao");

        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        EnvironmentVariable variable = variableDao.loadById(ENV_LAST_SYNC, appDef);

        if (variable == null) {
            variable = new EnvironmentVariable();
            variable.setId(ENV_LAST_SYNC);
            variable.setAppDefinition(appDef);
            variable.setValue(utc.toString());

            variableDao.add(variable);

            LogUtil.info(getClass().getName(), "Created ENV variable " + ENV_LAST_SYNC + " → " + utc);

        } else {

            variable.setValue(utc.toString());
            variableDao.update(variable);

            LogUtil.info(getClass().getName(), "Updated ENV variable " + ENV_LAST_SYNC + " → " + utc);
        }
    }

    private String formatUtc(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    private String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/SyncTenantCalendarEventsTool.json", null, true, null);
    }
}