package com.rostra.auction.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates calls from other services via the X-Service-Token header. Deliberately not a Spring bean:
 * it is wired into the security chain only (see SecurityConfig), so Boot does not also register it globally.
 */
public class ServiceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Service-Token";
    public static final String AUTHORITY_BIDDING = "SERVICE_BIDDING";
    private static final String BIDDING = "bidding-service";

    private final JwtService jwtService;

    public ServiceTokenFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        if (token != null && jwtService.isValidServiceToken(token, BIDDING)) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            BIDDING, null, List.of(new SimpleGrantedAuthority(AUTHORITY_BIDDING))));
        }
        chain.doFilter(request, response);
    }
}
