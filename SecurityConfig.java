package com.jobportal.config;

import com.jobportal.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig — the heart of RBAC.
 *
 * RBAC rules defined here (URL-level):
 *   PUBLIC:      /api/auth/**  (login, register)
 *   PUBLIC GET:  /api/jobs/** (anyone can browse jobs)
 *   EMPLOYER:    POST/PUT/DELETE /api/jobs (only employers post jobs)
 *   JOB_SEEKER:  POST /api/applications (only seekers apply)
 *   EMPLOYER:    PATCH /api/applications/{id}/status (employer updates status)
 *   ADMIN:       /api/users (only admin manages all users)
 *   ANY AUTH:    everything else requires login
 *
 * Fine-grained RBAC on individual methods uses @PreAuthorize (see controllers).
 * EnableMethodSecurity turns that on.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // enables @PreAuthorize on controller methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final UserDetailsService userDetailsService;

    // ── Security filter chain ─────────────────────────────────────────────────
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — we use stateless JWT, not sessions
            .csrf(csrf -> csrf.disable())

            // No sessions — JWT is our state
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ── RBAC RULES ────────────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth

                // Public endpoints — no token needed
                .requestMatchers("/api/auth/**").permitAll()

                // Anyone can browse open jobs (including guests)
                .requestMatchers(HttpMethod.GET, "/api/jobs/**").permitAll()

                // Only EMPLOYERs can create, edit, delete jobs
                .requestMatchers(HttpMethod.POST, "/api/jobs").hasRole("EMPLOYER")
                .requestMatchers(HttpMethod.PUT, "/api/jobs/**").hasRole("EMPLOYER")
                .requestMatchers(HttpMethod.DELETE, "/api/jobs/**").hasRole("EMPLOYER")
                .requestMatchers(HttpMethod.PATCH, "/api/jobs/*/status").hasRole("EMPLOYER")

                // Only JOB_SEEKERs can submit applications
                .requestMatchers(HttpMethod.POST, "/api/applications").hasRole("JOB_SEEKER")

                // Only EMPLOYERs can update application status (shortlist/reject/hire)
                .requestMatchers(HttpMethod.PATCH, "/api/applications/*/status").hasRole("EMPLOYER")

                // Only ADMINs can manage all users
                .requestMatchers("/api/users/**").hasRole("ADMIN")

                // Everything else — must be logged in (any role)
                .anyRequest().authenticated()
            )

            // Register our JWT filter BEFORE Spring's default auth filter
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ── Authentication provider — uses DB + BCrypt ────────────────────────────
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // ── AuthenticationManager — needed for login endpoint ────────────────────
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // ── BCrypt password encoder ───────────────────────────────────────────────
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
        // BCrypt automatically salts and hashes. Never store plain passwords.
    }
}
