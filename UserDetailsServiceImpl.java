package com.jobportal.security;

import com.jobportal.model.User;
import com.jobportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Security calls loadUserByUsername() automatically during auth.
 * We load the user from PostgreSQL and wrap it in Spring's UserDetails format.
 *
 * The role is prefixed with "ROLE_" — Spring Security requirement.
 * So User.Role.EMPLOYER becomes "ROLE_EMPLOYER" in Spring's world.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                    new UsernameNotFoundException("User not found: " + email)
                );

        // Spring Security needs roles prefixed with ROLE_
        String role = "ROLE_" + user.getRole().name(); // e.g. ROLE_EMPLOYER

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(new SimpleGrantedAuthority(role))
                .build();
    }
}
