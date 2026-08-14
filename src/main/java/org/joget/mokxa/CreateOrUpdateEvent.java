package org.joget.mokxa;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.joget.mokxa.model.ApiResponse;
import org.joget.mokxa.util.CustomTimeZoneUtil;
import org.joget.mokxa.util.EventUtil;
import org.joget.mokxa.util.LoginUtil;

import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CreateOrUpdateEvent extends FormBinder implements FormStoreBinder, FormStoreElementBinder, FormStoreMultiRowElementBinder {

    private  String CURRENT_USERNAME;

    @Override
    public FormRowSet store(Element element, FormRowSet rows, FormData formData) {


        String formDefId = getPropertyString("formDefId");

        String subjectField     = getPropertyString("subjectField");
        String locationField    = getPropertyString("locationField");
        String descriptionField = getPropertyString("descriptionField");

        String fromDateField      = getPropertyString("fromDateField");
        String toDateField        = getPropertyString("toDateField");
        String fromDateTimeField  = getPropertyString("fromDateTimeField");
        String toDateTimeField    = getPropertyString("toDateTimeField");

        String allDayField        = getPropertyString("allDayField");
        String enableMeetingField = getPropertyString("enableMeetingField");
        String isRecurringField   = getPropertyString("isRecurringField");

        String internalParticipantsField = getPropertyString("internalParticipantsField");
        String externalParticipantsField = getPropertyString("externalParticipantsField");

        String recurrenceTypeField       = getPropertyString("recurrenceTypeField");
        String recurrenceIntervalField   = getPropertyString("recurrenceIntervalField");
        String recurrenceDaysField       = getPropertyString("recurrenceDaysField");
        String recurrenceEndDateField    = getPropertyString("recurrenceEndDateField");

        String extendedPropGuid       = getPropertyString("extendedPropGuid");
        String extendedPropName       = getPropertyString("extendedPropName");
        String extendedPropFormField  = getPropertyString("extendedPropFormField");

        String is24Format = getPropertyString("is24Format");
        boolean use24Format = "true".equalsIgnoreCase(is24Format);

        String apiUsernameField = getPropertyString("apiUsernameField");

//        LogUtil.info(getClassName(),"apiUsernameField: "+apiUsernameField);



        boolean hasError = false;

        if (rows != null && !rows.isEmpty()) {
            FormRow row = rows.get(0);
            try {
                if (apiUsernameField != null && !apiUsernameField.isEmpty()) {
                    CURRENT_USERNAME = row.getProperty(apiUsernameField);
//                    LogUtil.info(getClassName(),"Current Username via form: "+CURRENT_USERNAME);
                }

                if (CURRENT_USERNAME == null || CURRENT_USERNAME.isEmpty()) {
                    CURRENT_USERNAME = WorkflowUtil.getCurrentUsername();
                    //LogUtil.info(getClassName(),"Current Username via Current user: "+CURRENT_USERNAME);
                }


                EventUtil eventUtil = new EventUtil(LoginUtil.getAccessToken(CURRENT_USERNAME),buildExtendedPropId(),CURRENT_USERNAME);
//                LogUtil.info("Rows",rows.toString());
                showFormData(formData);
                String subject = row.getProperty(subjectField);
                String location = row.getProperty(locationField);
                String description = row.getProperty(descriptionField);
                String extendedValue = row.getProperty(extendedPropFormField);

                //String timeZone= CustomTimeZoneUtil.getTimeZone(CURRENT_USERNAME);

                String timeZone= "UTC";

                boolean enableMeeting = "true".equalsIgnoreCase(row.getProperty(enableMeetingField));
                boolean isAllDay = "true".equalsIgnoreCase(row.getProperty(allDayField));
                boolean isRecurring="true".equalsIgnoreCase(row.getProperty(isRecurringField));

                if(enableMeeting){
                    location="Teams Meeting";
                }

                String from ;
                String to;

                if (isAllDay) {
                    String startDate = row.getProperty(fromDateField);
                    String endDate   = row.getProperty(toDateField);

                    // For all-day events, we keep the date but ensure it's in UTC format with Z
                    from = startDate + "T00:00:00Z";
                    to   = endDate + "T00:00:00Z";
                    row.setProperty(fromDateField,from);
                    row.setProperty(toDateField,to);
                } else {
                    // Convert user local time to UTC
                    String fromDateTimeValue = row.getProperty(fromDateTimeField);
                    String toDateTimeValue   = row.getProperty(toDateTimeField);

                    if (!use24Format) {
                        fromDateTimeValue = convert12HourTo24Hour(fromDateTimeValue);
                        toDateTimeValue   = convert12HourTo24Hour(toDateTimeValue);
                    }
                    from = CustomTimeZoneUtil.convertUserZoneToUtc(fromDateTimeValue, CURRENT_USERNAME);
                    to   = CustomTimeZoneUtil.convertUserZoneToUtc(toDateTimeValue, CURRENT_USERNAME);

                    row.setProperty(fromDateTimeField,from);
                    row.setProperty(toDateTimeField,to);
                }

                JSONArray attendees = buildAttendees(row);
                JSONObject recurrence = buildRecurrence(row);

                ApiResponse apiResponse;
                String eventId = row.getId();




                if (eventId == null || eventId.isEmpty()) {
                    apiResponse = eventUtil.createEvent(subject, description, location, from, to, isAllDay, timeZone, attendees, enableMeeting, recurrence,extendedValue);
                } else {
                    ApiResponse eventResponse = eventUtil.getEvent(eventId);
                    if (eventResponse == null || eventResponse.getResponseCode() != 200) {
                        apiResponse = eventUtil.createEvent(subject, description, location, from, to, isAllDay, timeZone, attendees, enableMeeting, recurrence,extendedValue);
                    } else {
                        apiResponse = compareAndUpdateEvent(eventUtil,eventResponse,eventId, subject, description, location, from, to, isAllDay, timeZone, attendees, enableMeeting, isRecurring,recurrence,extendedValue);
                    }
                }

                if (apiResponse == null || apiResponse.getResponseCode() >= 300) {
                    String errorMessage = "Failed to create/update Microsoft calendar event.";

                    if (apiResponse != null && apiResponse.getResponseBody() != null) {
                        try {
                            JSONObject responseJson = new JSONObject(apiResponse.getResponseBody());
                            JSONObject errorObj = responseJson.optJSONObject("error");
                            if (errorObj != null) {
                                String graphMessage = errorObj.optString("message");
                                if (graphMessage != null && !graphMessage.isEmpty()) {
                                    errorMessage = graphMessage;
                                }
                            }
                        } catch (Exception ignore) {}
                    }

                    formData.addFormError(subjectField, errorMessage);

                    LogUtil.error( getClass().getName(), null, "Microsoft Graph error → " + (apiResponse != null ? apiResponse.getResponseBody() : "No response"));

                    hasError = true;
                } else {
                    JSONObject responseJson = new JSONObject(apiResponse.getResponseBody());
                    row.setId(responseJson.optString("id"));
                    // Organizer
                    JSONObject organizer = responseJson.optJSONObject("organizer");
                    if (organizer != null) {
                        JSONObject email = organizer.optJSONObject("emailAddress");
                        if (email != null) {
                            String organizerEmail = email.optString("address");
                            set(row, "organizerField", organizerEmail);
                        }
                    }

                    JSONObject meeting = responseJson.optJSONObject("onlineMeeting");
                    if (meeting != null) {
                        String joinUrl = meeting.optString("joinUrl");
                        set(row, "meetingLinkField", joinUrl);
                    }

                    String seriesMasterId = responseJson.optString("seriesMasterId");
                    if (seriesMasterId != null && !seriesMasterId.isEmpty()) {
                        set(row, "seriesMasterIdField", seriesMasterId);
                    }

                    String webLink = responseJson.optString("webLink");
                    if (webLink != null && !webLink.isEmpty()) {
                        set(row, "eventUrlField", webLink);
                    }

                }

            } catch (Exception e) {
                formData.addFormError(subjectField, "Unexpected error while creating event.");
                LogUtil.error(getClass().getName(), e, "Event creation failed");
                hasError = true;
            }
        }

        if (hasError) {
            return null;
        }

        PluginManager pluginManager =
                (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");

        FormStoreBinder binder = (FormStoreBinder)
                pluginManager.getPlugin("org.joget.apps.form.lib.WorkflowFormBinder");

        binder.store(element, rows, formData);

        return rows;
    }


    private void showFormData(FormData formData) {
        JSONObject json = new JSONObject();
        Map<String, String[]> params = formData.getRequestParams();
        if (params != null) {
            for (Map.Entry<String, String[]> entry : params.entrySet()) {
                json.put(entry.getKey(), String.join(",", entry.getValue()));
            }
        }

//        LogUtil.info(getClass().getName(), "FormData params: " + json.toString());
    }

    public JSONArray buildAttendees(FormRow row) {
        String internalParticipantsField = getPropertyString("internalParticipantsField");
        String externalParticipantsField = getPropertyString("externalParticipantsField");

        String internalParticipants = row.getProperty(internalParticipantsField);
        String externalParticipants = row.getProperty(externalParticipantsField);

        JSONArray attendees = new JSONArray();

        if (internalParticipants != null && !internalParticipants.trim().isEmpty()) {
            Arrays.stream(internalParticipants.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(email -> attendees.put(createAttendee(email)));
        }

        if (externalParticipants != null && !externalParticipants.trim().isEmpty()) {
            Arrays.stream(externalParticipants.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(email -> attendees.put(createAttendee(email)));
        }

        return attendees;
    }

    private static JSONObject createAttendee(String email) {
        return new JSONObject()
                .put("emailAddress", new JSONObject()
                        .put("address", email))
                .put("type", "required");
    }

    public  JSONObject buildRecurrence(FormRow row) {

        // Recurrence
        String allDayField        = getPropertyString("allDayField");
        String isRecurringField   = getPropertyString("isRecurringField");
        String recurrenceTypeField       = getPropertyString("recurrenceTypeField");
        String recurrenceIntervalField   = getPropertyString("recurrenceIntervalField");
        String recurrenceDaysField       = getPropertyString("recurrenceDaysField");
        String recurrenceEndDateField    = getPropertyString("recurrenceEndDateField");

        // Date & time
        String fromDateField      = getPropertyString("fromDateField");
        String fromDateTimeField  = getPropertyString("fromDateTimeField");

        if (!"true".equalsIgnoreCase(row.getProperty(isRecurringField))) {
            return null;
        }

        JSONObject recurrence = new JSONObject();
        JSONObject pattern = new JSONObject();

        String rawType = row.getProperty(recurrenceTypeField);
        String type = mapRecurrenceType(rawType);
        if (type == null) {
            throw new IllegalArgumentException("Invalid recurrence_type: " + rawType);
        }

        int interval = 1;
        try {
            interval = Integer.parseInt(row.getProperty(recurrenceIntervalField));
        } catch (Exception ignored) {}

        pattern.put("type", type);
        pattern.put("interval", interval);

        // This part is actually correct - it extracts just the date:
        String startDate;
        if ("true".equalsIgnoreCase(row.getProperty(allDayField))) {
            String dt = row.getProperty(fromDateField);
            if (dt == null || dt.length() < 10) {
                throw new IllegalArgumentException("Invalid from_date");
            }
            startDate = dt.substring(0, 10);  // This is correct - just the date
        } else {
            String dt = row.getProperty(fromDateTimeField);
            if (dt == null || dt.length() < 10) {
                throw new IllegalArgumentException("Invalid from_date_time");
            }
            startDate = dt.substring(0, 10);  // This is correct - just the date
        }

        int dayOfMonth = Integer.parseInt(startDate.substring(8, 10));
        int month = Integer.parseInt(startDate.substring(5, 7));

        if ("weekly".equalsIgnoreCase(rawType)) {
            String daysValue = row.getProperty(recurrenceDaysField);
            if (daysValue == null || daysValue.trim().isEmpty()) {
                throw new IllegalArgumentException("Weekly recurrence requires daysOfWeek");
            }
            pattern.put("daysOfWeek", new JSONArray(Arrays.asList(daysValue.split(";"))));
            pattern.put("firstDayOfWeek", "Monday");
        }

        if ("monthly".equalsIgnoreCase(rawType)) {
            pattern.put("dayOfMonth", dayOfMonth);
        }

        if ("yearly".equalsIgnoreCase(rawType)) {
            pattern.put("month", month);
            pattern.put("dayOfMonth", dayOfMonth);
        }

        recurrence.put("pattern", pattern);

        String endDate = row.getProperty(recurrenceEndDateField);
        if (endDate == null || endDate.isEmpty()) {
            throw new IllegalArgumentException("recurrence_end_date is required");
        }

        JSONObject range = new JSONObject();
        range.put("type", "endDate");
        range.put("startDate", startDate);
        range.put("endDate", endDate);

        recurrence.put("range", range);

        return recurrence;
    }

    private static String mapRecurrenceType(String type) {
        if ("monthly".equalsIgnoreCase(type)) {
            return "absoluteMonthly";
        }
        if ("yearly".equalsIgnoreCase(type)) {
            return "absoluteYearly";
        }
        return type;
    }

    private String toIsoDateTime(String jogetDateTime) {
        if (jogetDateTime == null || jogetDateTime.isEmpty()) {
            return null;
        }

        try {
            SimpleDateFormat input =
                    new SimpleDateFormat("yyyy-MM-dd HH:mm");
            SimpleDateFormat output =
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

            return output.format(input.parse(jogetDateTime));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid Joget dateTime: " + jogetDateTime, e
            );
        }
    }

    private ApiResponse compareAndUpdateEvent( EventUtil eventUtil, ApiResponse eventResponse, String eventId, String subject, String description, String location, String startDateTime, String endDateTime, Boolean isAllDay, String userTimeZone, JSONArray attendees, Boolean onlineMeeting, Boolean isRecurring, JSONObject recurrence, String extendedValue) {

        JSONObject old = new JSONObject(eventResponse.getResponseBody());

        String updSubject =
                isDifferent(subject, old.optString("subject", null)) ? subject : null;

        String oldDesc =
                old.optJSONObject("body").optString("content", "");

        String newDesc =
                description == null ? "" : description;

        boolean descChanged =
                isDifferent(
                        cleanHtml(newDesc),
                        cleanHtml(oldDesc)
                );

        String updDesc = descChanged ? description : null;

        String updLocation =
                isDifferent(
                        location,
                        old.optJSONObject("location").optString("displayName", null)
                ) ? location : null;

        Boolean updAllDay =
                (isAllDay != null && isAllDay != old.optBoolean("isAllDay"))
                        ? isAllDay : null;


        boolean oldAllDay = old.optBoolean("isAllDay", false);
        boolean allDayChanged = isAllDay != null && isAllDay != oldAllDay;

        String updStart = null;
        String updEnd = null;

        if (allDayChanged || isTimeDifferent(
                startDateTime,
                old.optJSONObject("start").optString("dateTime"),
                userTimeZone,
                Boolean.TRUE.equals(isAllDay)
        )) {
            updStart = startDateTime;
        }

        if (allDayChanged || isTimeDifferent(
                endDateTime,
                old.optJSONObject("end").optString("dateTime"),
                userTimeZone,
                Boolean.TRUE.equals(isAllDay)
        )) {
            updEnd = endDateTime;
        }


        Boolean updOnline =
                (onlineMeeting != null &&
                        onlineMeeting != old.optBoolean("isOnlineMeeting"))
                        ? onlineMeeting : null;

        JSONArray updAttendees =
                isAttendeesDifferent(attendees, old.optJSONArray("attendees"))
                        ? attendees: null;

        GraphRecurrenceDiff rdiff =
                compareRecurrence(
                        isRecurring,
                        recurrence,
                        old.optJSONObject("recurrence")
                );

        
        String updMatter = compareSingleValueExtended(old, extendedValue);


        return eventUtil.updateEvent( eventId, updSubject, updDesc, updLocation, updStart, updEnd, updAllDay, userTimeZone, updAttendees, updOnline, rdiff.isRecurring, rdiff.recurrence,updMatter);
    }

    private String compareSingleValueExtended(JSONObject event, String newExtendedValue) {
        String oldExtendedValue = "";

        JSONArray props = event.optJSONArray("singleValueExtendedProperties");

        if (props != null && !props.isEmpty()) {
            oldExtendedValue = props
                    .optJSONObject(0)
                    .optString("value", "");
        }
        if (newExtendedValue == null && oldExtendedValue == null) {
            return null;
        }

        if (newExtendedValue == null || oldExtendedValue == null) {
            return newExtendedValue;
        }

        if (!newExtendedValue.equals(oldExtendedValue)) {
            return newExtendedValue;
        }

        return null;
    }

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
        return "Create / Update Microsoft Calendar Event";
    }

    @Override
    public String getDescription() {
        return "Create or update Microsoft Outlook calendar events from Joget forms, with support for Teams meetings, recurrence, participants, and automatic metadata synchronization.";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/CreateOrUpdateEvent.json", null, true, null);
    }

    private boolean isDifferent(String a, String b) {
        if (a == null && b == null) return false;
        if (a == null || b == null) return true;
        return !a.trim().equals(b.trim());
    }

    private String convertUserToUtc(String dateTimeStr, String userTimeZone) {

        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }

        dateTimeStr = dateTimeStr.replace("T", " ");

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        LocalDateTime localDateTime =
                LocalDateTime.parse(dateTimeStr, formatter);

        ZonedDateTime zonedDateTime =
                localDateTime.atZone(ZoneId.of(userTimeZone));

        Instant utcInstant = zonedDateTime.toInstant();

        return utcInstant.toString(); // e.g. 2026-01-16T05:00:00Z
    }

    private boolean isTimeDifferent( String formUserTime, String graphUtcTime, String userTimeZone, boolean isAllDay) {
        if (formUserTime == null && graphUtcTime == null) return false;
        if (formUserTime == null || graphUtcTime == null) return true;

        // ALL-DAY → compare DATE ONLY
        if (isAllDay) {
            String formDate  = extractDate(formUserTime);
            String graphDate = extractDate(graphUtcTime);
            return !formDate.equals(graphDate);
        }

        // For timed events, both should now be in UTC format
        if (graphUtcTime.contains(".")) {
            graphUtcTime = graphUtcTime.substring(0, graphUtcTime.indexOf(".")) + "Z";
        } else if (!graphUtcTime.endsWith("Z")) {
            graphUtcTime = graphUtcTime + "Z";
        }

        // Compare the UTC strings directly
        return !formUserTime.equals(graphUtcTime);
    }

    private Set<String> attendeeSet(JSONArray arr) {
        Set<String> set = new HashSet<>();
        if (arr == null) return set;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.optJSONObject(i);
            if (a == null) continue;

            JSONObject email = a.optJSONObject("emailAddress");
            if (email == null) continue;

            String addr = email.optString("address", null);
            if (addr != null) {
                set.add(addr.toLowerCase());
            }
        }
        return set;
    }

    private boolean isAttendeesDifferent(JSONArray form, JSONArray graph) {
        return !attendeeSet(form).equals(attendeeSet(graph));
    }

    private String extractDate(String dateTime) {
        
        if (dateTime == null || dateTime.length() < 10) {
            return null;
        }
        return dateTime.substring(0, 10); // yyyy-MM-dd
    }

    private GraphRecurrenceDiff compareRecurrence(boolean formRecurring, JSONObject formRecurrence, JSONObject graphRecurrence) {
        GraphRecurrenceDiff diff = new GraphRecurrenceDiff();
        if (formRecurring) {
            // non-recurring → recurring
            if (graphRecurrence == null) {
                diff.isRecurring = true;
                diff.recurrence = formRecurrence;
                return diff;
            }
            // recurring → recurring (but changed)
            if (!formRecurrence.similar(graphRecurrence)) {
                diff.isRecurring = true;
                diff.recurrence = formRecurrence;
                return diff;
            }
            // recurring → recurring (same)
            return diff; // null = skip PATCH
        }
        else {
            // recurring → non-recurring
            if (graphRecurrence != null) {
                diff.isRecurring = false;
                return diff;
            }
            // non-recurring → non-recurring
            return diff; // null = skip PATCH
        }
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

    private void set(FormRow row, String propertyKey, String value) {
        String field = getPropertyString(propertyKey);
        if (field != null && !field.isEmpty() && value != null) {
            row.setProperty(field, value);
        }
    }

    private String cleanHtml(String s) {

        if (s == null) return "";

        // remove html wrapper
        s = s.replaceAll("(?i)<html.*?>", "");
        s = s.replaceAll("(?i)</html>", "");

        // remove head
        s = s.replaceAll("(?i)<head.*?>.*?</head>", "");

        // remove body
        s = s.replaceAll("(?i)<body.*?>", "");
        s = s.replaceAll("(?i)</body>", "");

        // remove extra spaces
        s = s.replaceAll("\\s+", " ");

        // remove space before >
        s = s.replaceAll(">\\s+<", "><");

        return s.trim();
    }

    private String convert12HourTo24Hour(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return dateTimeStr;
        }

        try {
            DateTimeFormatter inputFormatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a", Locale.ENGLISH);

            DateTimeFormatter outputFormatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            LocalDateTime dateTime =
                    LocalDateTime.parse(dateTimeStr.trim().toUpperCase(), inputFormatter);

            return dateTime.format(outputFormatter);

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid 12-hour datetime format: " + dateTimeStr, e);
        }
    }

    private class GraphRecurrenceDiff {
        Boolean isRecurring = null;
        JSONObject recurrence = null;
    }

}

