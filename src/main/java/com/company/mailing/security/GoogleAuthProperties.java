package com.company.mailing.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.google")
public class GoogleAuthProperties {

    private String clientId = "";
    private String tokenInfoUrl = "https://oauth2.googleapis.com/tokeninfo";

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getTokenInfoUrl() {
        return tokenInfoUrl;
    }

    public void setTokenInfoUrl(String tokenInfoUrl) {
        this.tokenInfoUrl = tokenInfoUrl;
    }
}
