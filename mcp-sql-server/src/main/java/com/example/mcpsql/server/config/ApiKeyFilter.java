package com.example.mcpsql.server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates requests carrying the API key. Not a bean, so it only runs inside the security chain. */
class ApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-API-Key";

    private static final String PRINCIPAL = "mcp-client";
    private static final String AUTHORITY = "ROLE_MCP_CLIENT";

    private final byte[] apiKey;

    ApiKeyFilter(String apiKey) {
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented != null && MessageDigest.isEqual(apiKey, presented.getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    PRINCIPAL, null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
        }
        chain.doFilter(request, response);
    }
}
