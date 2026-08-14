package org.joget.mokxa;

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
import org.joget.mokxa.util.TenantCalendarSyncUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;

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


//        LogUtil.info(getClass().getName(), "==== Tenant Calendar Sync START ====");
//        LogUtil.info(getClass().getName(), "Props: "+props.toString());

        try {
            String tenantId     = getPropertyString("tenantId");
            String clientId     = getPropertyString("clientId");
            String clientSecret = getPropertyString("clientSecret");

            String formId    = getPropertyString("formDefId");
            String tableName =  getTableName(formId);

            Instant nowUtc = Instant.now();
            int fallbackHours = getInt("fallbackHours", 24);

            Instant lastSyncUtc = readLastSync(fallbackHours);

            int backMin = getInt("syncBackMinutes", 5);
            int forwardMin = getInt("syncForwardMinutes", 5);

            Instant startRange =
                    lastSyncUtc.minusSeconds(backMin * 60L);

            Instant endRange =
                    nowUtc.plusSeconds(forwardMin * 60L);

            String startUtcSync = formatUtc(startRange);
            String endUtcSync   = formatUtc(endRange);

//            LogUtil.info(getClass().getName(), "Sync Window UTC → " + startUtcSync + " to " + endUtcSync);

            TenantCalendarSyncUtil util = new TenantCalendarSyncUtil(tenantId, clientId, clientSecret);

            if (!util.authenticate()) {
                LogUtil.warn(getClass().getName(), "Authentication failed, aborting sync");
                return null;
            }

            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

            java.util.Set<String> processedEventIds = new java.util.HashSet<>();

            util.getAllUsers().forEach(user -> {

                String userId = user.getString("id");
                String userEmail = user.optString("mail", user.optString("userPrincipalName"));
//                LogUtil.info(getClass().getName(), "Sync User " + userEmail);

                JSONArray events = util.getUserEvents(userId, startUtcSync, endUtcSync,buildExtendedPropId());

                for (int i = 0; i < events.length(); i++) {

                    JSONObject event = events.getJSONObject(i);

                    if (event.optBoolean("isCancelled")) {
                        continue;
                    }

                    String eventId = event.optString("id");

                    if (eventId == null || eventId.isEmpty()) {
                        continue;
                    }

                    // skip duplicate in same sync run
                    if (processedEventIds.contains(eventId)) {

                        LogUtil.debug(
                                getClass().getName(),
                                "Skipping duplicate event in same sync → " + eventId
                        );

                        continue;
                    }

                    processedEventIds.add(eventId);

                    FormRow row = formDataDao.load(formId, tableName, eventId);
                    if (row == null) {
                        row = new FormRow();
                        row.setId(eventId);
                        row.setDateCreated(new Date());
                    }


                    set(row, "subjectField", event.optString("subject"));

                    boolean isAllDay = event.optBoolean("isAllDay", false);
                    set(row, "allDayField", String.valueOf(isAllDay));

                    if (event.has("location")) {
                        set(
                                row,
                                "locationField",
                                event.getJSONObject("location").optString("displayName")
                        );
                    }

                    if (event.has("body")) {
                        set(
                                row,
                                "descriptionField",
                                event.getJSONObject("body").optString("content")
                        );
                    }

                    JSONObject start = event.getJSONObject("start");
                    JSONObject end   = event.getJSONObject("end");

                    if (isAllDay) {

                        // store UTC date only
                        String startDate = start.getString("dateTime").substring(0, 10);
                        String endDate   = end.getString("dateTime").substring(0, 10);

                        set(row, "fromDateField", startDate);
                        set(row, "toDateField", endDate);

                    } else {

                        // store UTC datetime (no conversion)
                        String startUtc = start.getString("dateTime");
                        String endUtc   = end.getString("dateTime");

                        // normalize format (remove milliseconds)
                        startUtc = normalizeUtc(startUtc);
                        endUtc   = normalizeUtc(endUtc);

                        set(row, "fromDateTimeField", startUtc);
                        set(row, "toDateTimeField", endUtc);
                    }



                    if (event.optBoolean("isOnlineMeeting", false)) {
                        JSONObject meeting = event.optJSONObject("onlineMeeting");
                        set(row, "enableMeetingField", "true");
                        if (meeting != null) {
                            set(row, "meetingLinkField", meeting.optString("joinUrl"));
                        }
                    } else {
                        set(row, "enableMeetingField", "false");
                    }

                    if (event.has("attendees") && event.optJSONArray("attendees") != null) {
                        splitAttendees(event.getJSONArray("attendees"), row);
                    }

                    if (event.has("recurrence") && event.optJSONObject("recurrence") != null) {
                        populateRecurrence(event.getJSONObject("recurrence"), row);
                    } else {
                        set(row, "isRecurringField", "false");
                    }

                    String seriesMasterId = event.optString("seriesMasterId", "");
                    if (!seriesMasterId.isEmpty()) {
                        set(row, "seriesMasterIdField", seriesMasterId);
                    }

                    String eventUrl = event.optString("webLink");
                    if (!eventUrl.isEmpty()) {
                        set(row, "eventUrlField", eventUrl);
                    }

                    JSONObject organizer = event.optJSONObject("organizer");
                    if (organizer != null && organizer.has("emailAddress")) {
                        set(
                                row,
                                "organizerField",
                                organizer.getJSONObject("emailAddress").optString("address")
                        );
                    }

                    JSONArray singleValueProps = event.optJSONArray("singleValueExtendedProperties",new  JSONArray());
//                    LogUtil.info(getClass().getName(), "Sync Single Value ExtendedProperties: "+singleValueProps.toString());
                    if (singleValueProps != null && !singleValueProps.isEmpty()) {
                        set(
                                row,
                                "extendedPropFormField",
                                singleValueProps.optJSONObject(0).optString("value", "")
                        );
                    }else{
                        set(
                                row,
                                "extendedPropFormField",
                                ""
                        );
                    }


                    FormRowSet rs = new FormRowSet();
                    rs.add(row);
                    formDataDao.saveOrUpdate(formId, tableName, rs);
                }
            });

            updateLastSync(nowUtc);

//            LogUtil.info(getClass().getName(), "==== Tenant Calendar Sync END ====");

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


    private Instant readLastSync(int fallbackHours) {

        EnvironmentVariableDao variableDao = (EnvironmentVariableDao) AppUtil.getApplicationContext().getBean("environmentVariableDao");

        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        EnvironmentVariable variable = variableDao.loadById(ENV_LAST_SYNC, appDef);

        if (variable == null || variable.getValue() == null || variable.getValue().isEmpty()) {

            Instant fallback = Instant.now().minusSeconds(fallbackHours * 3600L);

//            LogUtil.info(getClass().getName(), "Last sync not found, using fallback → " + fallback);

            return fallback;
        }

        try {
            return Instant.parse(variable.getValue());
        } catch (Exception e) {
            LogUtil.warn(getClass().getName(),
                    "Invalid last sync value, resetting");

            return Instant.now().minusSeconds(fallbackHours * 3600L);
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

//            LogUtil.info(getClass().getName(), "Created ENV variable " + ENV_LAST_SYNC + " → " + utc);

        } else {

            variable.setValue(utc.toString());
            variableDao.update(variable);

//            LogUtil.info(getClass().getName(), "Updated ENV variable " + ENV_LAST_SYNC + " → " + utc);
        }
    }

    private String formatUtc(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }


    private void set(FormRow row, String propertyKey, String value) {
        String field = getPropertyString(propertyKey);
        if (field != null && !field.isEmpty() && value != null) {
            row.setProperty(field, value);
        }
    }

    private void splitAttendees(JSONArray attendees, FormRow row) {
        StringBuilder internal = new StringBuilder();
        StringBuilder external = new StringBuilder();

        for (int i = 0; i < attendees.length(); i++) {
            JSONObject attendee = attendees.getJSONObject(i);
            String email = attendee
                    .getJSONObject("emailAddress")
                    .optString("address");

            if (isInternalUser(email)) {
                internal.append(email).append(";");
            } else {
                external.append(email).append(";");
            }
        }

        if (!internal.isEmpty()) {
            set(row, "internalParticipantsField", internal.substring(0, internal.length() - 1));
        }

        if (!external.isEmpty()) {
            set(row, "externalParticipantsField", external.substring(0, external.length() - 1));
        }
    }

    private void populateRecurrence(JSONObject recurrence, FormRow row) {

        set(row, "isRecurringField", "true");

        JSONObject pattern = recurrence.getJSONObject("pattern");
        JSONObject range = recurrence.getJSONObject("range");

        set(row, "recurrenceTypeField", pattern.getString("type"));
        set(row, "recurrenceIntervalField", String.valueOf(pattern.getInt("interval")));

        if (pattern.has("daysOfWeek")) {
            JSONArray days = pattern.getJSONArray("daysOfWeek");

            set(row, "recurrenceDaysField", String.join(";", days.toList().stream()
                    .map(Object::toString).toArray(String[]::new)));
        }

        row.setProperty("recurrence_end_type", range.getString("type"));

        if ("endDate".equals(range.getString("type"))) {
            set(row, "recurrenceEndDateField", range.getString("endDate"));
        }
    }

    private boolean isInternalUser(String email) {
        DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");

        String sql = "SELECT 1 FROM dir_user WHERE email = ? LIMIT 1";

        try (Connection con = ds.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e,
                    "Failed to check internal user for email: " + email);
        }
        return false;
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/SyncTenantCalendarEventsTool.json", null, true, null);
    }

    private String normalizeUtc(String utc) {

        if (utc == null) return null;

        if (utc.contains(".")) {
            utc = utc.substring(0, utc.indexOf(".")) + "Z";
        } else if (!utc.endsWith("Z")) {
            utc = utc + "Z";
        }

        return utc;
    }

    private int getInt(String prop, int def) {
        try {
            return Integer.parseInt(getPropertyString(prop));
        } catch (Exception e) {
            return def;
        }
    }
}