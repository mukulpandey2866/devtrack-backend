package com.devtrack.devtrack_backend.security;

import com.devtrack.devtrack_backend.entity.User;
import com.devtrack.devtrack_backend.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Loads the subject from the database so deactivated users and role changes take effect immediately. */
@Component
public class JwtUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public JwtUserAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = parseSubject(jwt.getSubject())
                .flatMap(userRepository::findById)
                .filter(User::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid token subject"));
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.from(user),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getPlatformRole().name())));
    }

    private Optional<UUID> parseSubject(String subject) {
        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException | NullPointerException ex) {
            return Optional.empty();
        }
    }
}
