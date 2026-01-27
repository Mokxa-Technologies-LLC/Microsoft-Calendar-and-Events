package org.mokxa.plugins;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.*;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mokxa.plugins.model.ApiResponse;
import org.mokxa.plugins.util.CustomTimeZoneUtil;
import org.mokxa.plugins.util.EventUtil;
import org.mokxa.plugins.util.LoginUtil;

import java.time.*;
import java.util.*;

public class EventList extends DataListBinderDefault {

    @Override
    public DataListColumn[] getColumns() {

        List<DataListColumn> columns = new ArrayList<>();

        columns.add(new DataListColumn("id", "ID", false));
        columns.add(new DataListColumn("subject", "Subject", true));
        columns.add(new DataListColumn("start", "Start", true));
        columns.add(new DataListColumn("end", "End", true));
        columns.add(new DataListColumn("allDay", "All Day", true));
        columns.add(new DataListColumn("location", "Location", true));
        columns.add(new DataListColumn("organizer", "Organizer", true));
        columns.add(new DataListColumn("teams", "Teams Meeting", true));
        columns.add(new DataListColumn("recurring", "Recurring", true));
        columns.add(new DataListColumn("seriesMasterId", "Series Master ID", true));
        columns.add(new DataListColumn("occurrenceId", "Occurrence ID", true));
        columns.add(new DataListColumn("onlineMeetingUrl", "Online Meeting URL", true));
        columns.add(new DataListColumn("filter", "Filter", false));

        columns.add(new DataListColumn("description", "Description", true));
        columns.add(new DataListColumn("participants", "Participants", true));
        columns.add(new DataListColumn("eventURL", "Event URL", true));

        columns.add(new DataListColumn("extendedProperty", "Extended Property", true));



        return columns.toArray(new DataListColumn[0]);
    }

    @Override
    public String getPrimaryKeyColumnName() {
        return "id";
    }

    @Override
    public DataListCollection getData( DataList dataList, Map properties, DataListFilterQueryObject[] filters, String sort, Boolean desc, Integer start, Integer rows) {

        DataListCollection result = new DataListCollection();
        FormRowSet rowSet = new FormRowSet();

        int pageSize = rows != null ? rows : 10;
        int skip = start != null ? start : 0;

        try {
            EventUtil eventUtil = new EventUtil(LoginUtil.getAccessToken(),buildExtendedPropId());

            ApiResponse response = fetchByFilterType( filters, eventUtil, pageSize, skip);


            if (response == null || response.getResponseCode() >= 300) {
                return result;
            }

            JSONObject json = new JSONObject(response.getResponseBody());
            JSONArray events = json.optJSONArray("value");

            if (events == null) {
                return result;
            }


            for (int i = 0; i < events.length(); i++) {

                JSONObject event = events.getJSONObject(i);

                String id=event.optString("id");
                String subject= event.optString("subject");
                String startDate=event.getJSONObject("start").optString("dateTime");
                String enddate= event.getJSONObject("end").optString("dateTime");
                boolean allDay= event.optBoolean("isAllDay",false);
                String location = event.optJSONObject("location") != null ? event.getJSONObject("location").optString("displayName") : "";
                String organizer=event.optJSONObject("organizer") != null ? event.getJSONObject("organizer").getJSONObject("emailAddress").optString("address") : "";
                boolean hasTeams = event.optBoolean("isOnlineMeeting",false);
                boolean isRecurring = "occurrence".equalsIgnoreCase(event.optString("type"));
                String seriesMasterId = event.optString("seriesMasterId","");
                String occurrenceId = event.optString("occurrenceId","");
                String meetingUrl = "";

                JSONObject onlineMeeting = event.optJSONObject("onlineMeeting");
                if (onlineMeeting != null) {
                    meetingUrl = onlineMeeting.optString("joinUrl", "");
                }
                if (meetingUrl.isEmpty()) {
                    meetingUrl = event.optString("onlineMeetingUrl", "");
                }

                if(!meetingUrl.isEmpty()){
                    location="Teams Meeting";
                }

                String startValue;
                String endValue;

                if (allDay) {
                    startValue = extractDate(startDate);
                    endValue   = extractDate(enddate);
                } else {
                    startValue = CustomTimeZoneUtil.convertUtcToUserZone(startDate);
                    endValue   = CustomTimeZoneUtil.convertUtcToUserZone(enddate);
                }


                String participants = "";

                JSONArray attendees = event.optJSONArray("attendees");
                if (attendees != null) {
                    List<String> emails = new ArrayList<>();
                    for (int a = 0; a < attendees.length(); a++) {
                        JSONObject att = attendees.getJSONObject(a);
                        JSONObject emailObj = att.optJSONObject("emailAddress");
                        if (emailObj != null) {
                            emails.add(emailObj.optString("address"));
                        }
                    }
                    participants = String.join(", ", emails);
                }

                String description = "";
                JSONObject body = event.optJSONObject("body");
                if (body != null) {
                    description = body.optString("content");
                }

                String eventURL = event.optString("webLink");

                String extendedProperty = "";

                JSONArray props = event.optJSONArray("singleValueExtendedProperties");
                if (props != null && !props.isEmpty()) {
                    extendedProperty = props
                            .optJSONObject(0)
                            .optString("value", "");
                }


                FormRow row = new FormRow();
                row.setProperty("id", id);
                row.setProperty("subject", subject);
                row.setProperty("start", startValue);
                row.setProperty("end", endValue);
                row.setProperty("allDay", allDay ? "Yes" : "No");
                row.setProperty("location", location);
                row.setProperty("organizer", organizer);
                row.setProperty("teams", hasTeams ? "Yes" : "No");
                row.setProperty("recurring", isRecurring ? "Yes" : "No");
                row.setProperty("seriesMasterId", seriesMasterId);
                row.setProperty("occurrenceId", occurrenceId);
                row.setProperty("participants", participants);
                row.setProperty("description", description);
                row.setProperty("eventURL", eventURL);

                row.setProperty("onlineMeetingUrl", meetingUrl);
                row.setProperty("extendedProperty", extendedProperty);

                rowSet.add(row);
            }

            result.addAll(rowSet);

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Error loading Microsoft calendar events");
        }

        return result;
    }


    @Override
    public int getDataTotalRowCount( DataList dataList, Map properties, DataListFilterQueryObject[] filters ) {

        String extendedPropertyFilterValue=null;
        try {
            EventUtil eventUtil = new EventUtil(LoginUtil.getAccessToken(),buildExtendedPropId());

            String filterType = "";
            String from = "";
            String to = "";

            for (DataListFilterQueryObject filter : filters) {

                String query = filter.getQuery();
                String[] values = filter.getValues();

                if (values == null || values.length == 0) {
                    continue;
                }

                if (query.contains("start")) {
                    from = values[0];
                }

                if (query.contains("end")) {
                    to = values[0];
                }

                if (query.contains("extendedProperty")) {
                    extendedPropertyFilterValue = values[0];
                }

                if (query.contains("filter")) {
                    for (String val : values) {
                        if (val == null) continue;

                        String clean = val.replace("%", "").trim().toLowerCase();

                        if (clean.contains("today")) {
                            filterType = "today";
                        } else if (clean.contains("week")) {
                            filterType = "week";
                        } else if (clean.contains("month")) {
                            filterType = "month";
                        } else if (clean.contains("range")) {
                            filterType = "range";
                        }
                    }
                }
            }

            switch (filterType) {

                case "today":
                    return eventUtil.countTodayEvents(extendedPropertyFilterValue);

                case "week":
                    return eventUtil.countThisWeekEvents(extendedPropertyFilterValue);

                case "month":
                    return eventUtil.countThisMonthEvents(extendedPropertyFilterValue);

                case "range":
                    if (!from.isEmpty() && !to.isEmpty()) {
                        Map<String, String> range =
                                buildRange(
                                        LocalDate.parse(from),
                                        LocalDate.parse(to)
                                );
                        return eventUtil.countEventsByRange(range,extendedPropertyFilterValue);
                    }
                    return 0;

                default:
                    return eventUtil.countTodayEvents(extendedPropertyFilterValue);
            }

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e,
                    "Error calculating total row count for calendar events");
            return 0;
        }
    }

    private ApiResponse fetchByFilterType(DataListFilterQueryObject[] filters, EventUtil util, int top, int skip) {

        String filterType = "";
        String from = "";
        String to = "";
        String extendedPropertyFilterValue = null;

        for (DataListFilterQueryObject filter : filters) {

            String query = filter.getQuery();
            String[] values = filter.getValues();

            if (values == null || values.length == 0) {
                continue;
            }

            if (query.contains("start")) {
                from = values[0];
            }

            if (query.contains("end")) {
                to = values[0];
            }

            if (query.contains("extendedProperty")) {
                extendedPropertyFilterValue = values[0];
            }

            if (query.contains("filter")) {
                for (String val : values) {
                    if (val == null) continue;

                    String clean = val.replace("%", "").trim().toLowerCase();
                    if (clean.contains("today")) {
                        filterType = "today";
                    }
                    else if (clean.contains("week")) {
                        filterType = "week";
                    }
                    else if (clean.contains("month")) {
                        filterType = "month";
                    }
                    else if (clean.contains("range")) {
                        filterType = "range";
                    }
                }
            }
        }

        switch (filterType) {

            case "today":
                return util.getTodayEvents(top, skip,extendedPropertyFilterValue);

            case "week":
                return util.getThisWeekEvents(top, skip,extendedPropertyFilterValue);

            case "month":
                return util.getThisMonthEvents(top, skip,extendedPropertyFilterValue);

            case "range":
                Map<String, String> range = buildRange(
                        LocalDate.parse(from),
                        LocalDate.parse(to)
                );
                return util.listEventsByRange(range, top, skip,extendedPropertyFilterValue);
            default:
                return util.getTodayEvents(top, skip,extendedPropertyFilterValue);
        }
    }

    public static Map<String, String> buildRange( LocalDate from, LocalDate to) {
        ZoneId userZone=CustomTimeZoneUtil.getUserZoneId();
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

    private String extractDate(String graphDateTime) {
        if (graphDateTime == null || graphDateTime.isEmpty()) {
            return null;
        }
        return graphDateTime.substring(0, 10)+" 00:00"; // yyyy-MM-dd
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
        return "Microsoft Event List";
    }

    @Override
    public String getDescription() {
        return "List and filter Microsoft Outlook calendar events in Joget using Microsoft Graph, with support for date ranges, recurrence, and Teams meetings.";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/EventList.json", null, true, null);
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



}