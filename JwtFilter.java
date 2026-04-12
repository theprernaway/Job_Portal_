package com.jobportal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtFilter runs ONCE per request (OncePerRequestFilter).
 *
 * What it does:
 *   1. Reads the "Authorization" header
 *   2. Extracts the Bearer token
 *   3. Checks Redis to see if token is blacklisted (logged out)
 *   4. Validates token with JwtUtil
 *   5. Loads user from DB
 *   6. Sets the authentication in Spring Security context
 *      so all downstream @PreAuthorize checks work
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final RedisTokenService redisTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {

            // Step 1: Check if token is blacklisted in Redis (user logged out)
            if (redisTokenService.isBlacklisted(token)) {
                log.warn("Blacklisted token attempt");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been invalidated");
                return;
            }

            // Step 2: Validate signature and expiry
            if (jwtUtil.isTokenValid(token)) {
                String email = jwtUtil.getUserEmail(token);

                // Step 3: Load full user from DB
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // Step 4: Create authentication object
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities() // roles/permissions
                        );
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Step 5: Set in security context — now all @PreAuthorize annotations work
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // Continue to next filter / controller
        filterChain.doFilter(request, response);
    }

    // Reads "Authorization: Bearer <token>" header and returns just the token
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7); // remove "Bearer " prefix
        }
        return null;
    }
}
