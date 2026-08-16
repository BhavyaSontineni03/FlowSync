package com.expensemanagement.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests from the Bearer JWT. Default path loads the user once
 * from the DB. Under loadtest ({@code app.security.jwt-user-lookup=false}) it
 * trusts verified JWT claims only so the timed write path does not pay two
 * Hibernate user lookups per request.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    @Value("${app.security.jwt-user-lookup:true}")
    private boolean jwtUserLookup;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);
            if (StringUtils.hasText(jwt)) {
                Claims claims = tokenProvider.parseClaims(jwt);
                if (claims != null && !tokenProvider.isExpired(claims)) {
                    UserDetails userDetails = resolveUserDetails(claims);
                    if (userDetails != null) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private UserDetails resolveUserDetails(Claims claims) {
        String username = claims.getSubject();
        if (!jwtUserLookup) {
            String role = claims.get("role", String.class);
            if (role == null || role.isBlank()) {
                role = "EMPLOYEE";
            }
            return User.withUsername(username)
                    .password("n/a")
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
                    .build();
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (username.equals(userDetails.getUsername())) {
            return userDetails;
        }
        return null;
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
