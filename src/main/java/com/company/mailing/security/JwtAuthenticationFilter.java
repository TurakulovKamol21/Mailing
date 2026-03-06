package com.company.mailing.security;

import com.company.mailing.dto.auth.SessionCheckReq;
import com.company.mailing.dto.auth.SessionCheckResp;
import com.company.mailing.feign.AuthClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthClient authClient;
    private final AppAuthProperties appAuthProperties;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AuthClient authClient,
            AppAuthProperties appAuthProperties
    ) {
        this.jwtService = jwtService;
        this.authClient = authClient;
        this.appAuthProperties = appAuthProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "/health".equals(path) || path.startsWith("/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();
        try {
            JwtPrincipal principal = jwtService.parseAccessToken(token);
            validateSessionIfNeeded(principal);

            List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(value -> value.startsWith("ROLE_") ? value : "ROLE_" + value)
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"detail\":\"Invalid or expired token.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void validateSessionIfNeeded(JwtPrincipal principal) {
        if (!appAuthProperties.isSessionCheckEnabled()) {
            return;
        }
        if (principal.userId() == null || principal.sid() == null) {
            throw new IllegalArgumentException("Session claims are missing in token.");
        }

        SessionCheckResp response = authClient.check(new SessionCheckReq(principal.userId(), principal.sid()));
        if (response == null || !response.active()) {
            throw new IllegalArgumentException("Session is inactive.");
        }
    }
}
