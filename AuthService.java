package com.jobportal.service;

import com.jobportal.dto.LoginRequestDto;
import com.jobportal.dto.LoginResponseDto;
import com.jobportal.dto.RegisterRequestDto;
import com.jobportal.exception.GlobalExceptionHandler.*;
import com.jobportal.model.User;
import com.jobportal.repository.UserRepository;
import com.jobportal.security.JwtUtil;
import com.jobportal.security.RedisTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final RedisTokenService redisTokenService;

    @Value("${jwt.expiration.ms}")
    private long jwtExpirationMs;

    // ── Register ──────────────────────────────────────────────────────────────
    @Transactional
    public LoginResponseDto register(RegisterRequestDto dto) {

        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new EmailAlreadyExistsException(
                "Email already registered: " + dto.getEmail()
            );
        }

        User user = User.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword())) // BCrypt hash!
                .phone(dto.getPhone())
                .role(dto.getRole() != null ? dto.getRole() : User.Role.JOB_SEEKER)
                .companyName(dto.getCompanyName())
                .companyWebsite(dto.getCompanyWebsite())
                .skills(dto.getSkills())
                .bio(dto.getBio())
                .build();

        userRepository.save(user);
        log.info("New user registered: {} ({})", user.getEmail(), user.getRole());

        // Issue token immediately after registration
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return LoginResponseDto.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole().name())
                .name(user.getName())
                .expiresInMs(jwtExpirationMs)
                .build();
    }

    // ── Login ─────────────────────────────────────────────────────────────────
    public LoginResponseDto login(LoginRequestDto dto) {
        try {
            // Spring Security authenticates: loads user from DB, checks BCrypt hash
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        log.info("User logged in: {}", user.getEmail());

        return LoginResponseDto.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole().name())
                .name(user.getName())
                .expiresInMs(jwtExpirationMs)
                .build();
    }

    // ── Logout ────────────────────────────────────────────────────────────────
    /**
     * On logout we blacklist the token in Redis.
     * TTL = jwt expiration time so Redis auto-cleans it.
     * After this, JwtFilter will reject the token on every future request.
     */
    public void logout(String token) {
        if (jwtUtil.isTokenValid(token)) {
            redisTokenService.blacklistToken(token, jwtExpirationMs);
            log.info("Token blacklisted on logout");
        }
    }
}
