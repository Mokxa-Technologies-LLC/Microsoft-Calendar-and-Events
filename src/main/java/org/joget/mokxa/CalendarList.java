package org.joget.mokxa;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.*;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.joget.mokxa.model.ApiResponse;
import org.joget.mokxa.util.CustomTimeZoneUtil;
import org.joget.mokxa.util.EventUtil;
import org.joget.mokxa.util.LoginUtil;
import java.time.*;
import java.util.*;

public class CalendarList extends DataListBinderDefault {

    private  String CURRENT_USERNAME;

    @Override
    public DataListColumn[] getColumns() {

        List<DataListColumn> columns = new ArrayList<>();

        columns.add(new DataListColumn("id", "ID", false));
        columns.add(new DataListColumn("subject", "Subject", true));
        columns.add(new DataListColumn("startDate", "Start Date", true));
        columns.add(new DataListColumn("startTime", "Start Time", true));
        columns.add(new DataListColumn("endDate", "End Date", true));
        columns.add(new DataListColumn("endTime", "End Time", true));
        columns.add(new DataListColumn("allDay", "All Day", true));
        columns.add(new DataListColumn("location", "Location", true));
        columns.add(new DataListColumn("organizer", "Organizer", true));
        columns.add(new DataListColumn("teams", "Teams Meeting", true));
        columns.add(new DataListColumn("recurring", "Recurring", true));
        columns.add(new DataListColumn("eventURL", "Event URL", true));
        columns.add(new DataListColumn("color", "Color", true));
        columns.add(new DataListColumn("seriesMasterId", "Series Master ID", true));
        columns.add(new DataListColumn("occurrenceId", "Occurrence ID", true));
        columns.add(new DataListColumn("onlineMeetingUrl", "Online Meeting URL", true));
        columns.add(new DataListColumn("participants", "Participants", true));
        columns.add(new DataListColumn("description", "Description", true));
        columns.add(new DataListColumn("extendedProperty", "Extended Property", true));
        return columns.toArray(new DataListColumn[0]);
    }

    @Override
    public String getPrimaryKeyColumnName() {
        return "id";
    }

    @Override
    public DataListCollection getData(DataList dataList, Map properties, DataListFilterQueryObject[] filters, String sort, Boolean desc, Integer start, Integer rows) {

        logFilters(filters);

        DataListCollection result = new DataListCollection();
        FormRowSet rowSet = new FormRowSet();

        CURRENT_USERNAME= WorkflowUtil.getCurrentUsername();
        String is24Format = getPropertyString("is24Format");
        boolean use24Format = "true".equalsIgnoreCase(is24Format);

        start =0;
        rows=10;
        int count=0;

        try {
            EventUtil eventUtil = new EventUtil(LoginUtil.getAccessToken(CURRENT_USERNAME),buildExtendedPropId(),CURRENT_USERNAME);

            while (true) {

                ApiResponse response =
                        fetchByFilterRange(filters, eventUtil,start,rows);

                if (response == null || response.getResponseCode() >= 300) {
                    break;
                }

                JSONObject json = new JSONObject(response.getResponseBody());
                JSONArray events = json.optJSONArray("value");

                if (events == null || events.length() == 0) {
                    break;
                }

                for (int i = 0; i < events.length(); i++) {

                    JSONObject event = events.getJSONObject(i);

                    String id = event.optString("id");
                    String subject = event.optString("subject");

                    String startDate = event.getJSONObject("start").optString("dateTime");
                    String enddate   = event.getJSONObject("end").optString("dateTime");

                    boolean allDay = event.optBoolean("isAllDay", false);

                    String location = event.optJSONObject("location") != null ? event.getJSONObject("location").optString("displayName") : "";

                    String organizer = event.optJSONObject("organizer") != null ? event.getJSONObject("organizer").getJSONObject("emailAddress").optString("address") : "";

                    boolean hasTeams = event.optBoolean("isOnlineMeeting", false);
                    boolean isRecurring = "occurrence".equalsIgnoreCase(event.optString("type"));

                    String seriesMasterId = event.optString("seriesMasterId", "");
                    String occurrenceId  = event.optString("occurrenceId", "");

                    String startDateValue;
                    String startTimeValue;
                    String endDateValue;
                    String endTimeValue;

                    if (allDay) {
                        startDateValue = startDate.substring(0, 10);
                        endDateValue   = enddate.substring(0, 10);
                        startTimeValue = "";
                        endTimeValue   = "";
                    } else {
                        String startLocal = CustomTimeZoneUtil.convertUtcToUserZone(startDate,CURRENT_USERNAME);
                        String endLocal = CustomTimeZoneUtil.convertUtcToUserZone(enddate,CURRENT_USERNAME);

                        startDateValue = startLocal.substring(0, 10);
                        startTimeValue = startLocal.substring(11);
                        endDateValue   = endLocal.substring(0, 10);
                        endTimeValue   = endLocal.substring(11);

                        if (!use24Format) {
                            startTimeValue = convert24HourTimeTo12Hour(startTimeValue);
                            endTimeValue   = convert24HourTimeTo12Hour(endTimeValue);
                        }
                    }

                    String participants = "";
                    JSONArray attendees = event.optJSONArray("attendees");
                    if (attendees != null) {
                        List<String> emails = new ArrayList<>();
                        for (int a = 0; a < attendees.length(); a++) {
                            JSONObject emailObj = attendees.getJSONObject(a).optJSONObject("emailAddress");
                            if (emailObj != null) {
                                emails.add(emailObj.optString("address"));
                            }
                        }
                        participants = String.join(", ", emails);
                    }

                    String description = event.optJSONObject("body") != null ? event.getJSONObject("body").optString("content") : "";

                    String eventURL = event.optString("webLink");

                    String meetingUrl = event.optJSONObject("onlineMeeting") != null ? event.getJSONObject("onlineMeeting").optString("joinUrl", "") : event.optString("onlineMeetingUrl", "");

                    if (!meetingUrl.isEmpty()) {
                        location = "Teams Meeting";
                    }

                    String extendedProperty = "";

                    JSONArray props = event.optJSONArray("singleValueExtendedProperties");
                    if (props != null && !props.isEmpty()) {
                        extendedProperty = props
                                .optJSONObject(0)
                                .optString("value", "");
                    }


                    String allDayColor =
                            Optional.ofNullable(getPropertyString("allDayColor")).orElse("");

                    String teamsColor =
                            Optional.ofNullable(getPropertyString("teamsMeetingColor")).orElse("#6366f1");

                    String recurringColor =
                            Optional.ofNullable(getPropertyString("recurringEventColor")).orElse("");

                    String defaultColor =
                            Optional.ofNullable(getPropertyString("defaultEventColor")).orElse("#22c55e");

                    String color =
                            (allDay && !allDayColor.isEmpty()) ? allDayColor
                                    : hasTeams ? teamsColor
                                      : (isRecurring && !recurringColor.isEmpty()) ? recurringColor
                                        : defaultColor;

                    FormRow row = new FormRow();
                    row.setProperty("id", id);
                    row.setProperty("subject", subject);
                    row.setProperty("startDate", startDateValue);
                    row.setProperty("startTime", startTimeValue);
                    row.setProperty("endDate", endDateValue);
                    row.setProperty("endTime", endTimeValue);
                    row.setProperty("allDay",String.valueOf(allDay));//String.valueOf(allDay)
                    row.setProperty("location", location);
                    row.setProperty("organizer", organizer);
                    row.setProperty("teams", String.valueOf(hasTeams));
                    row.setProperty("recurring", String.valueOf(isRecurring));
                    row.setProperty("eventURL", eventURL);
                    row.setProperty("color", color);
                    row.setProperty("participants", participants);
                    row.setProperty("description", description);
                    row.setProperty("seriesMasterId", seriesMasterId);
                    row.setProperty("occurrenceId", occurrenceId);
                    row.setProperty("onlineMeetingUrl", meetingUrl);
                    row.setProperty("extendedProperty", extendedProperty);

                    rowSet.add(row);
                    count++;

//                    LogUtil.info("Event: "+count,
//                                    startDateValue + " → " + endDateValue + "  " +
//                                            startTimeValue + " → " + endTimeValue +
//                                    " | allDay=" + allDay + " " +subject
//                    );



                }

                //  Move to next page
                if (events.length() < rows) {
                    break; // no more pages
                }

                start += rows;
            }

            result.addAll(rowSet);

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Error loading Microsoft calendar events");
        }

        return result;
    }

    @Override
    public int getDataTotalRowCount( DataList dataList, Map properties, DataListFilterQueryObject[] filters ) {
        return 0;
    }

    private ApiResponse fetchByFilterRange(DataListFilterQueryObject[] filters, EventUtil util,int skip, int rows) {
        LocalDate from = extractFromDate(filters);
        LocalDate to   = extractToDate(filters);

        String filterValue=null;


        if (from == null || to == null) {
            return util.getThisMonthEvents(rows,skip,filterValue);
        }
        Map<String, String> range = buildRange(from, to);
        return util.listEventsByRange(range,rows,skip,filterValue);
    }

    public  Map<String, String> buildRange( LocalDate from, LocalDate to) {
        ZoneId userZone=CustomTimeZoneUtil.getUserZoneId(CURRENT_USERNAME);
        Map<String, String> map = new HashMap<>();

        ZonedDateTime startUtc =
                from.atStartOfDay(userZone)
                        .withZoneSameInstant(ZoneOffset.UTC);

        ZonedDateTime endUtc =
                to.atTime(23, 59, 59)
                        .atZone(userZone)
                        .withZoneSameInstant(ZoneOffset.UTC);

        map.put("start", startUtc.toString());
        map.put("end", endUtc.toString());

        return map;
    }

    private void logFilters(DataListFilterQueryObject[] filters) {

        if (filters == null || filters.length == 0) {
//            LogUtil.info(getClass().getName(), "No filters applied");
            return;
        }

        for (int i = 0; i < filters.length; i++) {
            DataListFilterQueryObject f = filters[i];

//            LogUtil.info(getClass().getName(),
//                    "Filter[" + i + "]" +
//                            " | query=" + f.getQuery() +
//                            " | operator=" + f.getOperator() +
//                            " | values=" + Arrays.toString(f.getValues())
//            );
        }
    }

    private LocalDate extractFromDate(DataListFilterQueryObject[] filters) {
        for (DataListFilterQueryObject f : filters) {
            String[] values = f.getValues();
            if (values != null && values.length >= 2) {
                return LocalDate.parse(values[0].substring(0, 10));
            }
        }
        return null;
    }

    private LocalDate extractToDate(DataListFilterQueryObject[] filters) {
        for (DataListFilterQueryObject f : filters) {
            String[] values = f.getValues();
            if (values != null && values.length >= 2) {
                return LocalDate.parse(values[1].substring(0, 10));
            }
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
        return "Microsoft Calendar List";
    }

    @Override
    public String getDescription() {
        return "Load Microsoft Outlook calendar events into Joget calendar views with date/time separation, colors, recurrence, and Teams support.";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/CalendarList.json", null, true, null);
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

    private String convert24HourTimeTo12Hour(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return timeStr;
        }

        try {
            java.time.format.DateTimeFormatter inputFormatter =
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm");

            java.time.format.DateTimeFormatter outputFormatter =
                    java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.ENGLISH);

            java.time.LocalTime time =
                    java.time.LocalTime.parse(timeStr.trim(), inputFormatter);

            return time.format(outputFormatter);

        } catch (Exception e) {
            LogUtil.warn(getClass().getName(), "Invalid 24-hour time format: " + timeStr);
            return timeStr;
        }
    }

}