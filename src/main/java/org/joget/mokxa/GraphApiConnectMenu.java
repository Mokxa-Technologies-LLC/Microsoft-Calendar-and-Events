package org.joget.mokxa;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.commons.util.LogUtil;
import org.joget.mokxa.dao.GraphAuthDao;
import org.joget.mokxa.model.GraphAuth;
import org.joget.workflow.util.WorkflowUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Content;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.InputStream;
import java.sql.*;

import com.fasterxml.jackson.databind.ObjectMapper;

//This plugin allows users to connect to Microsoft Graph API via OAuth2 and store the token in PostgreSQL, enabling integration with Microsoft services.


public class GraphApiConnectMenu extends UserviewMenu implements PluginWebSupport {

    private static final String TAG = "GraphApiConnectMenu";
    private static final boolean PLUGINDEBUGMODE = true;


    @Override
    public String getName() {
        return "Graph API Connect";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Connect to Microsoft Graph via OAuth2 and store token in PostgreSQL";
    }

    @Override
    public String getLabel() {
        return "Graph API Connect";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        try {
            InputStream in = this.getClass().getResourceAsStream("/properties/GraphApiConnectMenu.json");
            if (in != null) {
                return IOUtils.toString(in, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Unable to load property options");
        }
        return "{}";
    }

    @Override
    public String getIcon() {
        return "fa fa-windows";
    }

    public String getMenuIcon() {
        return getIcon();
    }

    @Override
    public String getRenderPage() {
        LogUtil.info(getClassName(), "getRenderPage() called");

        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");

        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("element", this);

        // Get OAuth data
        Object data = getData();
        dataModel.put("data", data);

        // App Composer Preview Page
        boolean isPreview = "true".equals(getRequestParameterString("isPreview"));
        dataModel.put("isPreview", isPreview);

        LogUtil.info(getClassName(), "Rendering template with isPreview: " + isPreview);

        return pluginManager.getPluginFreeMarkerTemplate(dataModel, getClassName(), "/templates/GraphApiConnectMenu.ftl", null);
    }

    @Override
    public String getCategory() {
        return "Integration";
    }

    @Override
    public String getDecoratedMenu() {
        return null;
    }

    @Override
    public boolean isHomePageSupported() {
        return false;
    }

    /**
     * WebService endpoint for OAuth callback URL
     * URL Pattern: https://lmsdev.mymokxa.com/web/json/plugin/org.joget.marketplace.lmsgraph.GraphApiConnectMenu/service
     */
    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            if (PLUGINDEBUGMODE) {
                LogUtil.info(TAG, "=== WEBSERVICE OAUTH CALLBACK STARTED ===");
                LogUtil.info(TAG, "INCOMING METHOD: " + request.getMethod());
                LogUtil.info(TAG, "INCOMING URI: " + request.getRequestURI());
                LogUtil.info(TAG, "INCOMING QUERY: " + request.getQueryString());
                LogUtil.info(TAG, "INCOMING SESSION: " + request.getSession().getId());
            }

            String code = request.getParameter("code");
            String state = request.getParameter("state");
            String error = request.getParameter("error");
            String errorDesc = request.getParameter("error_description");

            String successRedirectUrl ="";


            LogUtil.info(TAG, "OAuth callback params - code: " + (code != null ? "[present]" : "[null]") +
                    ", state: " + state + ", error: " + error);

            String currentUser = WorkflowUtil.getCurrentUsername();
            LogUtil.info(TAG, "Current Joget user: " + currentUser);

            // OAuth error
            if (error != null) {
                LogUtil.error(TAG, null, "OAuth error received: " + error + " - " + errorDesc);
                response.setContentType("text/html");
                PrintWriter out = response.getWriter();
                out.println("<html><body>");
                out.println("<h2>Authentication Error</h2>");
                out.println("<p>Error: " + error + "</p>");
                if (errorDesc != null) {
                    out.println("<p>Description: " + errorDesc + "</p>");
                }
                out.println("<p><a href='javascript:window.close()'>Close this window</a></p>");
                out.println("</body></html>");
                out.flush();
                return;
            }

            // Exchange code for token
            if (StringUtils.isNotBlank(code)) {
                LogUtil.info(TAG, "Authorization code received, starting token exchange...");

                // Decode state parameter to get configuration
                String clientId = "";
                String clientSecret = "";
                String tenant = "common";
                String redirectUri = "";
                String scopes = "";

                if (StringUtils.isNotBlank(state)) {
                    try {
                        String stateJson = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
                        ObjectMapper om = new ObjectMapper();
                        Map<String, String> stateData = om.readValue(stateJson, Map.class);

                        clientId = stateData.getOrDefault("clientId", "");
                        clientSecret = stateData.getOrDefault("clientSecret", "");
                        tenant = stateData.getOrDefault("tenant", "common");
                        redirectUri = stateData.getOrDefault("redirectUri", "");
                        scopes = stateData.getOrDefault("scopes", "");

                        successRedirectUrl = stateData.getOrDefault(
                                "successRedirectUrl",
                                ""
                        );

                        LogUtil.info(TAG, "Successfully decoded state parameter");
                    } catch (Exception e) {
                        LogUtil.error(TAG, e, "Failed to decode state parameter");
                    }
                }

                LogUtil.info(TAG, "Config - clientId: " + (StringUtils.isNotBlank(clientId) ? "[configured]" : "[MISSING]") +
                        ", clientSecret: " + (StringUtils.isNotBlank(clientSecret) ? "[configured]" : "[MISSING]") +
                        ", tenant: " + tenant + ", redirectUri: " + redirectUri);

                // Exchange code for token
                String tokenEndpoint = "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token";
                LogUtil.info(TAG, "Calling token endpoint: " + tokenEndpoint);

                Content tokenResponse = Request.Post(tokenEndpoint)
                        .bodyForm(
                                Form.form()
                                        .add("client_id", clientId)
                                        .add("client_secret", clientSecret)
                                        .add("code", code)
                                        .add("grant_type", "authorization_code")
                                        .add("redirect_uri", redirectUri)
                                        .add("scope", scopes)
                                        .build()
                        ).execute().returnContent();

                String json = tokenResponse.asString();
                LogUtil.info(TAG, "Token response received, parsing JSON...");

                ObjectMapper om = new ObjectMapper();
                Map<String, Object> tokenResp = om.readValue(json, Map.class);

                // Extract fields
                String accessToken = (String) tokenResp.get("access_token");
                String refreshToken = (String) tokenResp.get("refresh_token");
                String idToken = (String) tokenResp.get("id_token");
                String scope = (String) tokenResp.get("scope");
                Integer expiresIn = (Integer) tokenResp.get("expires_in");

                LogUtil.info("System time:", String.valueOf(System.currentTimeMillis()));
                // Calculate expiry timestamp
                long expiresAtMillis = System.currentTimeMillis() + (expiresIn != null ? expiresIn * 1000L : 3600000L);
                java.sql.Timestamp expiresAt = new java.sql.Timestamp(expiresAtMillis);
                LogUtil.info("Expires At:", String.valueOf(expiresAt));

                LogUtil.info(TAG, "Token extracted - accessToken: " + (accessToken != null ? "[present]" : "[MISSING]") +
                        ", refreshToken: " + (refreshToken != null ? "[present]" : "[MISSING]") +
                        ", idToken: " + (idToken != null ? "[present]" : "[null]") +
                        ", scope: " + scope + ", expiresIn: " + expiresIn + "s, expiresAt: " + expiresAt);

                // Parse id_token for oid/upn
                String msOid = null;
                String msUpn = null;
                if (idToken != null) {
                    LogUtil.info(TAG, "Parsing ID token for user identity...");
                    String[] parts = idToken.split("\\.");
                    if (parts.length >= 2) {
                        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                        Map<String, Object> claims = om.readValue(payload, Map.class);
                        msOid = (String) claims.get("oid");
                        msUpn = (String) claims.get("upn");
                        if (msUpn == null) {
                            msUpn = (String) claims.get("preferred_username");
                        }
                        LogUtil.info(TAG, "Parsed ID token - oid: " + msOid + ", upn: " + msUpn);
                    }
                }

                String id = UUID.randomUUID().toString();
                LogUtil.info(TAG, "Generated record ID: " + id);

                // Store token to DB
                byte[] tokenCacheEnc = accessToken != null ? accessToken.getBytes(StandardCharsets.UTF_8) : new byte[0];

                LogUtil.info(TAG, "Storing token to database...");
                //storeTokenToDb(id, currentUser, tenant, msOid == null ? "" : msOid, msUpn, scope, tokenCacheEnc, expiresAt,refreshToken);
                storeTokenToDb(id, currentUser, tenant, msOid == null ? "" : msOid, msUpn, scope, tokenCacheEnc, expiresAt, refreshToken, json);
                LogUtil.info(TAG, "Token stored successfully to database");

                // Redirect after successful authentication
                //String redirectUrl = "https://lmsuat.mymokxa.com/jw/web/userview/lms/v/_/2DA46020ED4A47D162E5C719CEB6BDC7";


                LogUtil.info(TAG, "Redirecting to: " + successRedirectUrl);
                response.sendRedirect(successRedirectUrl);

                LogUtil.info(TAG, "=== OAUTH CALLBACK COMPLETED SUCCESSFULLY ===");
            } else {
                LogUtil.error(TAG, null, "No authorization code received in callback");
                response.setContentType("text/html");
                PrintWriter out = response.getWriter();
                out.println("<html><body>");
                out.println("<h2>Invalid Request</h2>");
                out.println("<p>No authorization code received.</p>");
                out.println("</body></html>");
                out.flush();
            }

        } catch (Exception e) {
            LogUtil.error(TAG, e, "Error in webService OAuth callback");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/html");
            PrintWriter out = response.getWriter();
            out.println("<html><body>");
            out.println("<h2>Error</h2>");
            out.println("<p>An error occurred: " + e.getMessage() + "</p>");
            out.println("</body></html>");
            out.flush();
        }
    }

    // Check connection status and generate auth URL if needed
    public Object getData() {
        LogUtil.info(TAG, "=== getData() called - checking connection status ===");

        Map<String, Object> data = new HashMap<>();
        String currentUser = WorkflowUtil.getCurrentUsername();
        data.put("username", currentUser);
        data.put("connected", false);

        LogUtil.info(TAG, "Current Joget user: " + currentUser);

        try {
            // Check if user already has valid
            String accessToken = getStoredAccessToken(currentUser);

            if (accessToken != null) {
                LogUtil.info(TAG, "User has existing access token stored");
                data.put("connected", true);
                data.put("access_token", accessToken);
            } else {
                LogUtil.info(TAG, "No stored token found, generating Microsoft sign-in URL...");

                // Generate auth URL
                String clientId = getPropertyString("clientId");
                String clientSecret = getPropertyString("clientSecret");
                String tenant = getPropertyString("tenantId");
                if (StringUtils.isBlank(tenant)) {
                    tenant = "common";
                }
                String scopes = getPropertyString("scopes");
                String redirectUri = getPropertyString("redirectUri");

                LogUtil.info(TAG, "Auth config - clientId: " + (StringUtils.isNotBlank(clientId) ? "[configured]" : "[MISSING]") +
                        ", tenant: " + tenant + ", scopes: " + scopes + ", redirectUri: " + redirectUri);

                if (StringUtils.isBlank(redirectUri)) {
                    LogUtil.error(TAG, null, "Redirect URI is not configured");
                    data.put("error", "Redirect URI is required. Please configure it in plugin settings.");
                    return data;
                }

                // Encode config in state parameter so callback can access it
                Map<String, String> stateData = new HashMap<>();
                stateData.put("clientId", clientId);
                stateData.put("clientSecret", clientSecret);
                stateData.put("tenant", tenant);
                stateData.put("redirectUri", redirectUri);
                stateData.put("scopes", scopes);
                stateData.put(
                        "successRedirectUrl",
                        getPropertyString("successRedirectUrl")
                );

                ObjectMapper om = new ObjectMapper();
                String stateJson = om.writeValueAsString(stateData);
                String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateJson.getBytes(StandardCharsets.UTF_8));

                String authUrl = "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/authorize" +
                        "?client_id=" + urlEncode(clientId) +
                        "&response_type=code" +
                        "&redirect_uri=" + urlEncode(redirectUri) +
                        "&response_mode=query" +
                        "&scope=" + urlEncode(scopes) +
                        "&state=" + state;

                LogUtil.info(TAG, "Generated auth URL (length: " + authUrl.length() + ")");
                data.put("authUrl", authUrl);
            }
        } catch (Exception e) {
            LogUtil.error(TAG, e, "Error checking connection status");
            data.put("error", "Error checking connection: " + e.getMessage());
        }

        LogUtil.info(TAG, "=== getData() completed - connected: " + data.get("connected") + " ===");
        return data;
    }

    /**
     * Check if user has a stored access token in the database
     * Returns the access token
     * If expired or missing, returns null which will trigger OAuth login
     */
    protected String getStoredAccessToken(String username) {

        GraphAuthDao dao =
                (GraphAuthDao) GraphAppContext
                        .getInstance()
                        .getAppContext()
                        .getBean("graphAuthDao");

        GraphAuth auth = dao.getLatest(username);

        if (auth == null) return null;

        if (auth.getExpiresAt() == null) return null;

        if (System.currentTimeMillis() < auth.getExpiresAt().getTime()) {

            return auth.getAccessTokenDebug();
        }

        return null;
    }

//    protected void storeTokenToDb(
//            String id,
//            String jogetUsername,
//            String tenantId,
//            String msOid,
//            String msUpn,
//            String scopes,
//            byte[] tokenCacheEnc,
//            Timestamp expiresAt,
//            String accessTokenDebug
//    ) {
//
//        GraphAuthDao dao =
//                (GraphAuthDao) GraphAppContext
//                        .getInstance()
//                        .getAppContext()
//                        .getBean("graphAuthDao");
//
//        GraphAuth a = new GraphAuth();
//
//        a.setId(id);
//        a.setJogetUsername(jogetUsername);
//        a.setTenantId(tenantId);
//        a.setMsOid(msOid);
//        a.setMsUpn(msUpn);
//
//        a.setScopes(scopes);
//        a.setTokenCacheEnc(tokenCacheEnc);
//
//        a.setExpiresAt(expiresAt);
//
//        a.setStatus("ACTIVE");
//        a.setCacheFormat("msal4j_v1");
//
//        a.setLastTokenRefreshAt(new Timestamp(System.currentTimeMillis()));
//
//        a.setAccessTokenDebug(accessTokenDebug);
//
//        dao.save(a);
//    }

    protected void storeTokenToDb(String id, String jogetUsername, String tenantId,
                                  String msOid, String msUpn, String scopes,
                                  byte[] tokenCacheEnc, Timestamp expiresAt,
                                  String refreshToken, String accessTokenDebug) {
        GraphAuthDao dao = (GraphAuthDao) GraphAppContext.getInstance()
                .getAppContext().getBean("graphAuthDao");
        GraphAuth a = new GraphAuth();
        a.setId(id);
        a.setJogetUsername(jogetUsername);
        a.setTenantId(tenantId);
        a.setMsOid(msOid);
        a.setMsUpn(msUpn);
        a.setScopes(scopes);
        a.setTokenCacheEnc(tokenCacheEnc);
        a.setExpiresAt(expiresAt);
        a.setRefreshToken(refreshToken);          // ← new
        a.setCacheFormat("msal4j_v1");
        a.setStatus("ACTIVE");
        a.setLastTokenRefreshAt(new Timestamp(System.currentTimeMillis()));
        a.setAccessTokenDebug(accessTokenDebug);  // full JSON response
        dao.saveOrUpdate(a);                      // ← upsert method
    }


    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

}