package com.company.mailing.security;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.auth")
public class AppAuthProperties {

    private String roles = "USER";
    private long sessionTtlMinutes = 60;
    private boolean sessionCheckEnabled;
    private String serviceUrl = "http://192.168.1.96:8081";

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public long getSessionTtlMinutes() {
        return sessionTtlMinutes;
    }

    public void setSessionTtlMinutes(long sessionTtlMinutes) {
        this.sessionTtlMinutes = sessionTtlMinutes;
    }

    public boolean isSessionCheckEnabled() {
        return sessionCheckEnabled;
    }

    public void setSessionCheckEnabled(boolean sessionCheckEnabled) {
        this.sessionCheckEnabled = sessionCheckEnabled;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public List<String> resolveRoles() {
        String rawRoles = roles == null || roles.isBlank() ? "USER" : roles;
        List<String> resolved = Arrays.stream(rawRoles.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toUpperCase)
                .toList();
        return resolved.isEmpty() ? List.of("USER") : resolved;
    }
}
