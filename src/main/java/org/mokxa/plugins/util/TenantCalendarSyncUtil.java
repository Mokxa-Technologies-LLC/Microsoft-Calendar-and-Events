package org.mokxa.plugins.util;

import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TenantCalendarSyncUtil {

    private String tenantId;
    private String clientId;
    private String clientSecret;
    private String accessToken;

    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";


    public TenantCalendarSyncUtil(String tenantId, String clientId, String clientSecret) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public boolean authenticate() {

        LogUtil.info(getClass().getName(), "Authenticating (APP permission) for tenant=" + tenantId);

        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {

            HttpPost post = new HttpPost(tokenUrl);
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");

            String payload =
                    "grant_type=client_credentials" +
                            "&client_id=" + clientId +
                            "&client_secret=" + clientSecret +
                            "&scope=https%3A%2F%2Fgraph.microsoft.com%2F.default";

            post.setEntity(new StringEntity(payload));

            try (CloseableHttpResponse response = client.execute(post)) {

                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                int code = response.getStatusLine().getStatusCode();

                if (code == 200) {
                    accessToken = new JSONObject(body).getString("access_token");
                    LogUtil.info(getClass().getName(), "Access token acquired successfully");
                    return true;
                }

                LogUtil.warn(getClass().getName(), "Authentication failed → " + code + " → " + body);
            }
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Authentication error");
        }
        return false;
    }

    private JSONObject execute(HttpUriRequest request) throws Exception {

        request.setHeader("Authorization", "Bearer " + accessToken);
        request.setHeader("Accept", "application/json");

        LogUtil.info(getClass().getName(), request.getMethod() + " " + request.getURI());

        try (CloseableHttpClient client = HttpClientBuilder.create().build();
             CloseableHttpResponse response = client.execute(request)) {

            String body = response.getEntity() != null
                            ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                            : "{}";

            LogUtil.info(getClass().getName(), "Response Code → " + response.getStatusLine().getStatusCode());

            LogUtil.debug(getClass().getName(), "Response Body → " + body);

            return new JSONObject(body);
        }
    }

    public List<JSONObject> getAllUsers() {

        LogUtil.info(getClass().getName(), "Fetching all users from tenant");

        List<JSONObject> users = new ArrayList<>();
        String url = GRAPH_BASE + "/users?$select=id,mail,userPrincipalName";

        try {
            while (url != null) {

                JSONObject response = execute(new HttpGet(url));
                JSONArray values = response.optJSONArray("value");

                if (values != null) {
                    for (int i = 0; i < values.length(); i++) {
                        users.add(values.getJSONObject(i));
                    }
                }

                url = response.optString("@odata.nextLink", null);
            }

            LogUtil.info(getClass().getName(), "Total users fetched → " + users.size());

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Error fetching users");
        }

        return users;
    }


    public JSONArray getUserEvents( String userId, String startUtc, String endUtc ,String extendedPropId) {

        LogUtil.info(getClass().getName(), "Fetching events for user=" + userId + " range=" + startUtc + " → " + endUtc);

        String expand = "";

        if(extendedPropId!=null && !extendedPropId.isEmpty()){
            expand = "singleValueExtendedProperties($filter=id eq '" + extendedPropId + "')";
        }

        try {
            String url =
                    GRAPH_BASE + "/users/" + userId + "/calendarView" +
                            "?startDateTime=" + encode(startUtc) +
                            "&endDateTime=" + encode(endUtc) +
                            "&$select=id,subject,start,end,isAllDay,isCancelled" +
                            (extendedPropId.isEmpty()?"":"&$expand=" + encode(expand));

            JSONObject response = execute(new HttpGet(url));
            JSONArray events = response.optJSONArray("value");

            LogUtil.info(getClass().getName(), "Events fetched for user=" + userId + " → " + (events != null ? events.length() : 0));

            return events != null ? events : new JSONArray();

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Error fetching events for user=" + userId);
            return new JSONArray();
        }
    }

    public String extractExtendedPropValue(JSONObject event,String extendedPropId) {
        JSONArray props = event.optJSONArray("singleValueExtendedProperties");

        if (props == null || props.isEmpty()) {
            return "";
        }

        for (int i = 0; i < props.length(); i++) {
            JSONObject p = props.getJSONObject(i);
            if (extendedPropId.equals(p.optString("id"))) {
                return p.optString("value", "");
            }
        }
        return "";
    }

    private String encode(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}