package org.joget.mokxa.model;

import java.io.Serializable;
import java.sql.Timestamp;

public class GraphAuth implements Serializable {

    private String id;
    private String jogetUsername;
    private String tenantId;
    private String msOid;
    private String msUpn;
    private String scopes;

    private byte[] tokenCacheEnc;
    private String refreshToken;
    private Timestamp expiresAt;

    private String cacheFormat;
    private String status;

    private Timestamp lastTokenRefreshAt;
    private Timestamp updatedAt;

    private String accessTokenDebug;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getJogetUsername() {
        return jogetUsername;
    }

    public void setJogetUsername(String jogetUsername) {
        this.jogetUsername = jogetUsername;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getMsOid() {
        return msOid;
    }

    public void setMsOid(String msOid) {
        this.msOid = msOid;
    }

    public String getMsUpn() {
        return msUpn;
    }

    public void setMsUpn(String msUpn) {
        this.msUpn = msUpn;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public byte[] getTokenCacheEnc() {
        return tokenCacheEnc;
    }

    public void setTokenCacheEnc(byte[] tokenCacheEnc) {
        this.tokenCacheEnc = tokenCacheEnc;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getCacheFormat() {
        return cacheFormat;
    }

    public void setCacheFormat(String cacheFormat) {
        this.cacheFormat = cacheFormat;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getLastTokenRefreshAt() {
        return lastTokenRefreshAt;
    }

    public void setLastTokenRefreshAt(Timestamp lastTokenRefreshAt) {
        this.lastTokenRefreshAt = lastTokenRefreshAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getAccessTokenDebug() {
        return accessTokenDebug;
    }

    public void setAccessTokenDebug(String accessTokenDebug) {
        this.accessTokenDebug = accessTokenDebug;
    }
}w