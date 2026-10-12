package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.dto.LoginRequest;
import com.devtrack.devtrack_backend.dto.RegisterRequest;
import com.devtrack.devtrack_backend.dto.UserResponse;
import com.devtrack.devtrack_backend.entity.User;
import com.devtrack.devtrack_backend.enums.PlatformRole;
import com.devtrack.devtrack_backend.exception.BadRequestException;
import com.devtrack.devtrack_backend.exception.ConflictException;
import com.devtrack.devtrack_backend.exception.InvalidCredentialsException;
import com.devtrack.devtrack_backend.repository.UserRepository;
import com.devtrack.devtrack_backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerNormalizesEmailHashesPasswordAndForcesUserRole() {
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.register(new RegisterRequest(" Ada ", "Ada@Example.com", "correct-horse"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getValue().getFullName()).isEqualTo("Ada");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("correct-horse");
        assertThat(passwordEncoder.matches("correct-horse", saved.getValue().getPasswordHash())).isTrue();
        assertThat(response.platformRole()).isEqualTo(PlatformRole.USER);
    }

    @Test
    void registerRejectsExistingEmail() {
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ada", "ada@example.com", "correct-horse")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerMapsConcurrentUniqueViolationToConflict() {
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ada", "ada@example.com", "correct-horse")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void registerRejectsPasswordsOverBcryptByteLimit() {
        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ada", "ada@example.com", "é".repeat(40))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void loginRejectsUnknownEmailWrongPasswordAndInactiveUserIdentically() {
        User active = new User("Ada", "ada@example.com", passwordEncoder.encode("correct-horse"));
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(active));
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ada@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid email or password");
        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "correct-horse")))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid email or password");
        verify(jwtService, never()).issueAccessToken(any());
    }

    @Test
    void loginIssuesTokenForValidCredentials() {
        User user = new User("Ada", "ada@example.com", passwordEncoder.encode("correct-horse"));
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(jwtService.issueAccessToken(user)).thenReturn(new JwtService.IssuedToken("token-value", 900));

        var response = authService.login(new LoginRequest(" ADA@example.com ", "correct-horse"));

        assertThat(response.accessToken()).isEqualTo("token-value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
    }
}
