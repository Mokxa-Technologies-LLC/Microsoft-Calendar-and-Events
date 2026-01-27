package org.mokxa.plugins.util;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.joget.commons.util.LogUtil;
import org.mokxa.plugins.model.ApiResponse;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class EventUtil {
    private final String accessToken;

    private  final String EXTENDED_VALUE_PROP_ID;
    private  final String expand ;

    public EventUtil(String accessToken, String extendedValuePropID){
        this.accessToken=accessToken;
        this.EXTENDED_VALUE_PROP_ID=extendedValuePropID;
        if(extendedValuePropID!=null && !extendedValuePropID.isEmpty()){
            this.expand="singleValueExtendedProperties($filter=id eq '" + EXTENDED_VALUE_PROP_ID + "')";

        }else{
            this.expand=null;
        }
    }

    private ApiResponse execute(HttpUriRequest request) {
        ApiResponse apiResponse = new ApiResponse();

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {

            if (accessToken == null) {
                return null;
            }

            request.setHeader("Authorization", "Bearer " + accessToken);
            request.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = client.execute(request)) {
                apiResponse.setResponseCode(response.getStatusLine().getStatusCode());
                apiResponse.setResponseBody(
                        response.getEntity() != null
                                ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                                : ""
                );

                LogUtil.info(getClass().getName(),
                        request.getMethod() + " " + request.getURI()
                                + " → " + apiResponse.getResponseCode());

                LogUtil.info(getClass().getName(),
                        "Response "
                                + " → " + apiResponse.getResponseBody());

                return apiResponse;
            }
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e,
                    "Error executing request: " + request.getURI());
        }
        return null;
    }

    public ApiResponse createEvent( String subject, String description, String location, String startDateTime, String endDateTime, boolean isAllDay, String timeZone, JSONArray attendees, boolean onlineMeeting, JSONObject recurrence,String extendedValue) {
        try {
            JSONObject body = new JSONObject();

            body.put("subject", subject);
            body.put("body", new JSONObject()
                    .put("contentType", "HTML")
                    .put("content", description));

            if (location != null && !location.isEmpty()) {
                body.put("location",
                        new JSONObject().put("displayName", location));
            }

            if (isAllDay) {
                body.put("isAllDay", true);
            }

            body.put("start", new JSONObject()
                    .put("dateTime", startDateTime)
                    .put("timeZone", timeZone));

            body.put("end", new JSONObject()
                    .put("dateTime", endDateTime)
                    .put("timeZone", timeZone));

            if (attendees != null && !attendees.isEmpty()) {
                body.put("attendees", attendees);
            }

            if (onlineMeeting) {
                body.put("isOnlineMeeting", true);
                body.put("onlineMeetingProvider", "teamsForBusiness");
            }

            if (recurrence != null) {
                body.put("recurrence", recurrence);
            }

            if (extendedValue != null) {
                body.put("singleValueExtendedProperties", new JSONArray()
                        .put(new JSONObject()
                                .put("id", EXTENDED_VALUE_PROP_ID)
                                .put("value", extendedValue)
                        )
                );
            }

            LogUtil.info(getClass().getName(),"Request Body"+ body);

            HttpEntityEnclosingRequestBase request;
            request = new HttpPost("https://graph.microsoft.com/v1.0/me/events");

            request.setEntity(new StringEntity(body.toString(), ContentType.APPLICATION_JSON));

            return execute(request);

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Error Creating Event");
            return null;
        }
    }

    public ApiResponse updateEvent( String eventId, String subject, String description, String location, String startDateTime, String endDateTime, Boolean isAllDay, String timeZone, JSONArray attendees, Boolean onlineMeeting, Boolean isRecurring, JSONObject recurrence,String extendedValue) {
        try {
            JSONObject body = new JSONObject();

            if (subject != null && !subject.isEmpty()) {
                body.put("subject", subject);
            }

            if (description != null) {
                body.put("body", new JSONObject()
                        .put("contentType", "HTML")
                        .put("content", description));
            }

            if (location != null && !location.isEmpty()) {
                body.put("location",
                        new JSONObject().put("displayName", location));
            }

            if (isAllDay != null) {
                body.put("isAllDay", isAllDay);
            }

            if (startDateTime != null ) {
                body.put("start", new JSONObject()
                        .put("dateTime", startDateTime)
                        .put("timeZone", timeZone));
            }


            if (endDateTime != null) {
                body.put("end", new JSONObject()
                        .put("dateTime", endDateTime)
                        .put("timeZone", timeZone));
            }

            if (attendees != null) {
                body.put("attendees", attendees);
            }

            if (onlineMeeting != null) {
                body.put("isOnlineMeeting", onlineMeeting);
                if (onlineMeeting) {
                    body.put("onlineMeetingProvider", "teamsForBusiness");
                }
            }

            if (extendedValue != null) {
                body.put("singleValueExtendedProperties", new JSONArray()
                        .put(new JSONObject()
                                .put("id", EXTENDED_VALUE_PROP_ID)
                                .put("value", extendedValue)
                        )
                );
            }

            if (isRecurring != null) {
                if (isRecurring && recurrence != null) {
                    body.put("recurrence", recurrence);
                } else if (!isRecurring) {
                    body.put("recurrence", JSONObject.NULL);
                }
            }

            if (body.isEmpty()) {
                LogUtil.info(getClass().getName(),
                        "No changes detected, PATCH skipped for eventId=" + eventId);
                ApiResponse response = new ApiResponse();
                response.setResponseCode(200);
                response.setResponseBody(new JSONObject().put("id",eventId).toString());
                return response;
            }

            LogUtil.info(getClass().getName(), "PATCH Body → " + body);

            HttpPatch request = new HttpPatch(
                    "https://graph.microsoft.com/v1.0/me/events/" + eventId + "?sendUpdates=All"
            );

            request.setEntity(
                    new StringEntity(body.toString(), ContentType.APPLICATION_JSON)
            );

            return execute(request);

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Error updating event");
            return null;
        }
    }

    public ApiResponse getEvent(String eventId) {
        return execute(
                new HttpGet("https://graph.microsoft.com/v1.0/me/events/" + eventId
                        + (expand!=null?"?$expand=" + URLEncoder.encode(expand, StandardCharsets.UTF_8):"")
                )
        );
    }

    public ApiResponse deleteEvent(String eventId) {
        return execute(
                new HttpDelete("https://graph.microsoft.com/v1.0/me/events/" + eventId)
        );
    }

    public ApiResponse cancelEvent(String eventId, String comment) {
            JSONObject body = new JSONObject();
            if (comment != null && !comment.isEmpty()) {
                body.put("comment", comment);
            }

            LogUtil.info(getClass().getName(), "Cancel Event Body → " + body);
            HttpPost request = new HttpPost("https://graph.microsoft.com/v1.0/me/events/" + eventId + "/cancel");

            request.setEntity( new StringEntity(body.toString(), ContentType.APPLICATION_JSON));

            return execute(request);
    }

    public ApiResponse listEventsByRange(Map<String, String> range, int top, int skip,String filterExtendedValue) {

        StringBuilder url = new StringBuilder(
                "https://graph.microsoft.com/v1.0/me/calendarView"
                        + "?startDateTime=" + encode(range.get("start"))
                        + "&endDateTime=" + encode(range.get("end"))
                        + "&$orderby=start/dateTime"
                        + "&$top=" + top
                        + "&$skip=" + skip
                        + "&$select=id,subject,start,end,isAllDay,location,isOnlineMeeting,onlineMeeting,organizer,type,seriesMasterId,attendees,webLink,body"
        );

        if (filterExtendedValue != null && !filterExtendedValue.trim().isEmpty()) {

            String filter =
                    "singleValueExtendedProperties/any(ep:" +
                            "ep/id eq '" + EXTENDED_VALUE_PROP_ID + "' and " +
                            "ep/value eq '" + filterExtendedValue + "'" +
                            ")";
            url.append("&$filter=").append(encode(filter));
        }

        if(expand!=null){
            url.append("&$expand=").append(URLEncoder.encode(expand, StandardCharsets.UTF_8));
        }
        return execute(new HttpGet(url.toString()));
    }

    public int countEventsByRange(Map<String, String> range, String filterExtendedValue) {

        StringBuilder url = new StringBuilder(
                "https://graph.microsoft.com/v1.0/me/calendarView"
                        + "?startDateTime=" + encode(range.get("start"))
                        + "&endDateTime=" + encode(range.get("end"))
                        + "&$count=true"
                        + "&$top=1"
        );

        if (filterExtendedValue != null && !filterExtendedValue.trim().isEmpty()) {

            String filter =
                    "singleValueExtendedProperties/any(ep:" +
                            "ep/id eq '" + EXTENDED_VALUE_PROP_ID + "' and " +
                            "ep/value eq '" + filterExtendedValue + "'" +
                            ")";
            url.append("&$filter=").append(encode(filter));
        }

        if (expand != null) {
            url.append("&$expand=").append(
                    URLEncoder.encode(expand, StandardCharsets.UTF_8)
            );
        }

        HttpGet request = new HttpGet(url.toString());
        request.setHeader("ConsistencyLevel", "eventual");

        ApiResponse response = execute(request);

        if (response == null || response.getResponseCode() >= 300) {
            return 0;
        }

        try {
            JSONObject json = new JSONObject(response.getResponseBody());
            return json.optInt("@odata.count", 0);
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Error parsing count response");
            return 0;
        }
    }
    public ApiResponse getTodayEvents(int top, int skip,String filterExtendedValue) {
        return listEventsByRange(EventFilterUtil.today(), top, skip,filterExtendedValue);
    }

    public ApiResponse getThisWeekEvents(int top, int skip,String filterExtendedValue) {
        return listEventsByRange(EventFilterUtil.thisWeek(), top, skip,filterExtendedValue);
    }

    public ApiResponse getThisMonthEvents(int top, int skip,String filterExtendedValue) {
        return listEventsByRange(EventFilterUtil.thisMonth(), top, skip,filterExtendedValue);
    }

    public int countTodayEvents(String filterExtendedValue) {
        return countEventsByRange(EventFilterUtil.today(),filterExtendedValue);
    }

    public int countThisWeekEvents(String filterExtendedValue) {
        return countEventsByRange(EventFilterUtil.thisWeek(),filterExtendedValue);
    }

    public int countThisMonthEvents(String filterExtendedValue) {
        return countEventsByRange(EventFilterUtil.thisMonth(),filterExtendedValue);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

}