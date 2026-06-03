package org.joget.mokxa;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.sql.Timestamp;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.mokxa.dao.GraphAuthDao;
import org.joget.mokxa.model.GraphAuth;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.property.model.PropertyEditable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


public class MsGraphTokenRefreshTool extends DefaultApplicationPlugin implements PropertyEditable {
    private static final String TAG = "MsGraphTokenRefreshTool";
    private static final boolean DEBUG = true;
    private static final ObjectMapper objectMapper = new ObjectMapper();


    // Token refresh window 15 minutes = 900 seconds
    private static final long REFRESH_WINDOW_SECONDS = 900;

    @Override
    public String getName() {
        return "MS Graph Token Refresh Tool";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Refreshes Microsoft Graph API access tokens before they expire using stored refresh tokens";
    }

    @Override
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        try {
            InputStream in = this.getClass().getResourceAsStream("/properties/msGraphTokenRefreshTool.json");
            if (in != null) {
                return IOUtils.toString(in, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Unable to load property options");
        }
        return "{}";
    }

    @Override
    public Object execute(Map properties) {
        if (DEBUG) {
            LogUtil.info(TAG, "=== MS Graph Token Refresh Tool Started ===");
        }

        try {
            String clientSecret = getPropertyString("clientSecret");

            // Get refresh window configuration
            int refreshWindowMinutes = 15;
            String refreshWindowStr = getPropertyString("refreshWindowMinutes");
            if (StringUtils.isNotBlank(refreshWindowStr)) {
                try {
                    refreshWindowMinutes = Integer.parseInt(refreshWindowStr);
                } catch (NumberFormatException e) {
                    LogUtil.warn(TAG, "Invalid refreshWindowMinutes value, using default: 15");
                }
            }

            long refreshWindowSeconds = refreshWindowMinutes * 60;

            if (DEBUG) {
                LogUtil.info(TAG, "Configuration - clientSecret: " + (StringUtils.isNotBlank(clientSecret) ? "[configured]" : "[MISSING]") +
                        ", refreshWindow: " + refreshWindowMinutes + " minutes");
            }

            // Validate configuration
            if (StringUtils.isBlank(clientSecret)) {
                LogUtil.error(TAG, null, "Client Secret is required for token refresh");
                return null;
            }

            // Load tokens for refresh
            List<GraphAuth> tokensToRefresh = loadTokensNeedingRefresh(refreshWindowSeconds);
            LogUtil.info(TAG, "Found " + tokensToRefresh.size() + " token(s) that need refresh");

            int successCount = 0;
            int errorCount = 0;

            // Process each token
            for (GraphAuth auth : tokensToRefresh) {
                String recordId = auth.getId();
                String jogetUsername = auth.getJogetUsername();
                String msUpn = auth.getMsUpn();
                String refreshToken = auth.getRefreshToken();
                String scopes = auth.getScopes();
                Timestamp expiresAt = auth.getExpiresAt();
                String accessTokenDebug = auth.getAccessTokenDebug();
                String recordTenant = auth.getTenantId();

                if (StringUtils.isBlank(refreshToken)) {
                    LogUtil.warn(TAG, "No refresh token for user: " + jogetUsername + ", skipping");
                    errorCount++;
                    continue;
                }

                try {
                    // Extract client_id from the JWT token in access_token_debug column
                    String clientId = extractClientIdFromToken(accessTokenDebug);

                    if (StringUtils.isBlank(clientId)) {
                        LogUtil.error(TAG, null, "Could not extract client_id from token for user: " + jogetUsername + ", skipping");
                        errorCount++;
                        continue;
                    }

                    if (DEBUG) {
                        long currentTime = System.currentTimeMillis();
                        long expiryTime = expiresAt != null ? expiresAt.getTime() : 0;
                        long secondsRemaining = (expiryTime - currentTime) / 1000;

                        LogUtil.info(TAG, "Refreshing token for user: " + jogetUsername + " (" + msUpn + ")");
                        LogUtil.info(TAG, "  - Current expiry: " + expiresAt);
                        LogUtil.info(TAG, "  - Seconds until expiry: " + secondsRemaining);
                        LogUtil.info(TAG, "  - Client ID: " + clientId);
                        LogUtil.info(TAG, "  - Tenant ID: " + recordTenant);
                        LogUtil.info(TAG, "  - Scopes: " + scopes);
                    }

                    // Refresh the token
                    Map<String, Object> newTokenData = refreshAccessToken(
                            clientId,
                            clientSecret,
                            recordTenant,
                            refreshToken,
                            scopes
                    );

                    // Extract new token data
                    String newAccessToken = (String) newTokenData.get("access_token");
                    String newRefreshToken = (String) newTokenData.get("refresh_token");
                    Integer expiresIn = (Integer) newTokenData.get("expires_in");
                    String newScope = (String) newTokenData.get("scope");

                    // Calculate new expiry timestamp
                    long newExpiresAtMillis = System.currentTimeMillis() + (expiresIn != null ? expiresIn * 1000L : 3600000L);
                    Timestamp newExpiresAt = new Timestamp(newExpiresAtMillis);

                    if (DEBUG) {
                        LogUtil.info(TAG, "New token received - expiresIn: " + expiresIn + "s, newExpiresAt: " + newExpiresAt);
                    }

                    // Update database with new token
                    updateTokenInDb(
                            recordId,
                            newAccessToken,
                            newRefreshToken,
                            newExpiresAt,
                            newScope,
                            objectMapper.writeValueAsString(newTokenData)
                    );

                    LogUtil.info(TAG, "Successfully refreshed token for user: " + jogetUsername);
                    successCount++;

                } catch (Exception e) {
                    LogUtil.error(TAG, e, "Error refreshing token for user: " + jogetUsername);
                    errorCount++;
                }
            }

            LogUtil.info(TAG, "=== Token Refresh Completed - Success: " + successCount +
                    ", Errors: " + errorCount + " ===");

        } catch (Exception e) {
            LogUtil.error(TAG, e, "Fatal error in MsGraphTokenRefreshTool");
        }

        return null;
    }


    protected List<GraphAuth> loadTokensNeedingRefresh(long refreshWindowSeconds)
            throws Exception {

        GraphAuthDao dao =
                (GraphAuthDao) GraphAppContext
                        .getInstance()
                        .getAppContext()
                        .getBean("graphAuthDao");

        long thresholdMillis =
                System.currentTimeMillis()
                        + (refreshWindowSeconds * 1000);


        Timestamp threshold =
                new Timestamp(thresholdMillis);

        return dao.getTokensNeedingRefresh(threshold);

    }
    /**
     * Refresh access token using refresh token
     * @param clientId Azure application client ID
     * @param clientSecret Azure application client secret
     * @param tenantId Azure tenant ID
     * @param refreshToken The refresh token to use
     * @param scopes The scopes to request
     * @return Map containing new token data
     */
    protected Map<String, Object> refreshAccessToken(
            String clientId,
            String clientSecret,
            String tenantId,
            String refreshToken,
            String scopes) throws Exception {

        String tokenEndpoint = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";

        if (DEBUG) {
            LogUtil.info(TAG, "Calling token endpoint: " + tokenEndpoint);
        }

        // Build form data for token refresh
        Form form = Form.form()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken);

        // Add scopes
        if (StringUtils.isNotBlank(scopes)) {
            form.add("scope", scopes);
        }

        // Make request to Microsoft
        Content tokenResponse = Request.Post(tokenEndpoint)
                .bodyForm(form.build())
                .execute()
                .returnContent();

        String json = tokenResponse.asString();

        if (DEBUG) {
            LogUtil.info(TAG, "Token refresh response received, parsing JSON...");
        }

        // Parse response
        Map<String, Object> tokenData = objectMapper.readValue(json, Map.class);

        // Validate response
        if (!tokenData.containsKey("access_token")) {
            throw new Exception("Token refresh response does not contain access_token");
        }

        return tokenData;
    }

    /**
     * Update token in database
     * @param recordId The record ID to update
     * @param accessToken New access token
     * @param refreshToken New refresh token
     * @param expiresAt New expiry timestamp
     * @param scopes Updated scopes
     * @param accessTokenDebug Full token response as JSON for debugging
     */
    protected void updateTokenInDb(
            String recordId,
            String accessToken,
            String refreshToken,
            Timestamp expiresAt,
            String scopes,
            String accessTokenDebug) throws Exception {

        GraphAuthDao dao =
                (GraphAuthDao) GraphAppContext
                        .getInstance()
                        .getAppContext()
                        .getBean("graphAuthDao");

        GraphAuth a = dao.getById(recordId);

        if (a == null) return;

        byte[] tokenCacheEnc =
                accessToken != null
                        ? accessToken.getBytes(StandardCharsets.UTF_8)
                        : new byte[0];

        a.setTokenCacheEnc(tokenCacheEnc);
        a.setRefreshToken(refreshToken);
        a.setExpiresAt(expiresAt);
        a.setScopes(scopes);
        a.setAccessTokenDebug(accessTokenDebug);

        a.setLastTokenRefreshAt(
                new Timestamp(System.currentTimeMillis())
        );

        a.setUpdatedAt(
                new Timestamp(System.currentTimeMillis())
        );

        dao.save(a);

    }






    protected String extractClientIdFromToken(String accessTokenDebug) {
        if (StringUtils.isBlank(accessTokenDebug)) {
            return null;
        }

        try {
            String accessToken = null;

            // Check if it's JSON (starts with '{')
            String trimmed = accessTokenDebug.trim();
            if (trimmed.startsWith("{")) {
                // Parse as JSON and extract the access_token field
                JsonNode jsonNode = objectMapper.readTree(trimmed);
                if (jsonNode.has("access_token")) {
                    accessToken = jsonNode.get("access_token").asText();
                } else {
                    LogUtil.warn(TAG, "JSON token field does not contain 'access_token' property");
                    return null;
                }
            } else {

                accessToken = trimmed;
            }

            if (StringUtils.isBlank(accessToken)) {
                return null;
            }

            // JWT tokens are 3 parts separated by dots: header.payload.signature
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) {
                LogUtil.warn(TAG, "Invalid JWT token format - expected 3 parts");
                return null;
            }

            // Decode the payload
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

            // Parse payload JSON
            JsonNode payloadNode = objectMapper.readTree(payload);

            // Extract appid
            if (payloadNode.has("appid")) {
                String clientId = payloadNode.get("appid").asText();
                if (DEBUG) {
                    LogUtil.info(TAG, "Extracted client_id from JWT: " + clientId);
                }
                return clientId;
            } else {
                LogUtil.warn(TAG, "JWT payload does not contain 'appid' field");
                return null;
            }

        } catch (Exception e) {
            LogUtil.error(TAG, e, "Error extracting client_id from access token");
            return null;
        }
    }
}