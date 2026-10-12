package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.dto.AuthResponse;
import com.devtrack.devtrack_backend.dto.LoginRequest;
import com.devtrack.devtrack_backend.dto.RegisterRequest;
import com.devtrack.devtrack_backend.dto.UserResponse;
import com.devtrack.devtrack_backend.entity.User;
import com.devtrack.devtrack_backend.exception.BadRequestException;
import com.devtrack.devtrack_backend.exception.ConflictException;
import com.devtrack.devtrack_backend.exception.InvalidCredentialsException;
import com.devtrack.devtrack_backend.exception.ResourceNotFoundException;
import com.devtrack.devtrack_backend.mapper.UserMapper;
import com.devtrack.devtrack_backend.repository.UserRepository;
import com.devtrack.devtrack_backend.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final String EMAIL_TAKEN = "An account with this email already exists";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    // Compared against when the email is unknown so that response time does not reveal which emails exist.
    private final String timingEqualisationHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.timingEqualisationHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BadRequestException("Password must not exceed 72 bytes");
        }
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(EMAIL_TAKEN);
        }
        // Platform role is never taken from the request: public registration always yields USER.
        User user = new User(request.fullName().trim(), email, passwordEncoder.encode(request.password()));
        try {
            return UserMapper.toResponse(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(EMAIL_TAKEN);
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : timingEqualisationHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordMatches || !user.isActive()) {
            throw new InvalidCredentialsException();
        }
        JwtService.IssuedToken token = jwtService.issueAccessToken(user);
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), UserMapper.toResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
