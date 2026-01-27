package org.mokxa.plugins;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.*;
import org.joget.commons.util.LogUtil;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mokxa.plugins.model.ApiResponse;
import org.mokxa.plugins.util.CustomTimeZoneUtil;
import org.mokxa.plugins.util.EventUtil;
import org.mokxa.plugins.util.LoginUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LoadOrSyncEvent extends FormBinder implements FormLoadBinder , FormLoadElementBinder, FormLoadMultiRowElementBinder {

    @Override
    public String getName() {
        return getClass().getName();
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getLabel() {
        return "Load / Sync Microsoft Calendar Event";
    }

    @Override
    public String getDescription() {
        return "Load and synchronize Microsoft Outlook calendar events into Joget forms, including participants, Teams meetings, recurrence, and event metadata.";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/LoadOrSyncEvent.json", null, true, null);
    }

    @Override
    public FormRowSet load(Element element, String primaryKey, FormData formData) {





        FormRowSet rows = new FormRowSet();
        rows.setMultiRow(false);

        try {
            if (primaryKey == null || primaryKey.isEmpty()) {
                return rows;
            }

            EventUtil eventUtil = new EventUtil(LoginUtil.getAccessToken(),buildExtendedPropId());
            ApiResponse apiResponse = eventUtil.getEvent(primaryKey);

            if (apiResponse == null || apiResponse.getResponseCode() >= 300) {
                LogUtil.warn(getClass().getName(),
                        "Failed to load event from Microsoft for ID: " + primaryKey);
                return rows;
            }


            JSONObject event = new JSONObject(apiResponse.getResponseBody());

            String id = event.optString("id");
            String subject= event.optString("subject");
            JSONObject start = event.getJSONObject("start");
            JSONObject end = event.getJSONObject("end");
            boolean isAllDay = event.optBoolean("isAllDay", false);

            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            FormRow row = formDataDao.load("event","lms_event",primaryKey);

            if(row==null){
                row= new FormRow();
            }

            row.setProperty("id", id);
            set(row, "subjectField", subject);

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


            set(row, "allDayField", String.valueOf(isAllDay));

            String startLocal;
            String endLocal;
            if (isAllDay) {
                // DO NOT convert timezone for all-day events
                startLocal = extractDate(start.getString("dateTime"));
                endLocal   = extractDate(end.getString("dateTime"));
            } else {
                startLocal = CustomTimeZoneUtil.convertUtcToUserZone(
                        start.getString("dateTime")
                );
                endLocal = CustomTimeZoneUtil.convertUtcToUserZone(
                        end.getString("dateTime")
                );
            }

            LogUtil.info("Start Local [Sync]:",startLocal);
            LogUtil.info("Start Graph [Sync]:",start.getString("dateTime"));


            LogUtil.info("End Local:",endLocal);
            LogUtil.info("End Graph:",end.getString("dateTime"));


            if (isAllDay) {
                set(row, "fromDateField", startLocal.substring(0, 10));
                set(row, "toDateField", endLocal.substring(0, 10));
            } else {
                set(row, "fromDateTimeField", startLocal);
                set(row, "toDateTimeField", endLocal);
            }


            boolean hasMeeting = event.has("onlineMeeting");


            if (event.has("onlineMeeting")) {
                JSONObject meeting = event.optJSONObject("onlineMeeting");
                set(row, "enableMeetingField", String.valueOf(hasMeeting));

                if (meeting != null) {
                    set(row, "meetingLinkField", meeting.optString("joinUrl"));
                }
            }


            if (event.has("attendees")&& event.optJSONArray("attendees")!=null) {
                splitAttendees(event.getJSONArray("attendees"), row);
            }

            if (event.has("recurrence")&& event.optJSONObject("recurrence")!=null) {
                populateRecurrence(event.getJSONObject("recurrence"), row);
            } else {
                set(row, "isRecurringField", "false");
            }


            String seriesMasterId = event.optString("seriesMasterId","");
            if(!seriesMasterId.isEmpty()){
                set(row, "seriesMasterIdField", seriesMasterId);
            }

            String eventURL = event.optString("webLink");
            if(!eventURL.isEmpty()){
                set(row, "eventUrlField", eventURL);
            }


            String organizer=event.optJSONObject("organizer") != null ? event.getJSONObject("organizer").getJSONObject("emailAddress").optString("address") : "";

            if(!organizer.isEmpty()){
                set(row, "organizerField", organizer);
            }

            String extendedPropertyValue = "";
            JSONArray props = event.optJSONArray("singleValueExtendedProperties");
            if (props != null && !props.isEmpty()) {
                extendedPropertyValue = props
                        .optJSONObject(0)
                        .optString("value", "");
            }
            set(row, "extendedPropFormField", extendedPropertyValue);


            rows.add(row);

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Error loading Microsoft event");
        }

        return rows;
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

    private String extractDate(String graphDateTime) {
        if (graphDateTime == null || graphDateTime.isEmpty()) {
            return null;
        }

        // Graph format: yyyy-MM-ddTHH:mm:ss(.nanos)
        return graphDateTime.substring(0, 10);
    }


    private String buildExtendedPropId() {

        String guid = getPropertyString("extendedPropGuid");
        String name  = getPropertyString("extendedPropName");

        if (guid == null || guid.isEmpty()) {
            return null;
        }

        if (name == null || name.isEmpty()) {
            return null;
        }

        return "String {" + guid + "} Name " + name;
    }

    private void set(FormRow row, String propertyKey, String value) {
        String field = getPropertyString(propertyKey);
        if (field != null && !field.isEmpty() && value != null) {
            row.setProperty(field, value);
        }
    }

}
