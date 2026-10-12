package com.devtrack.devtrack_backend.repository;

import com.devtrack.devtrack_backend.AbstractIntegrationTest;
import com.devtrack.devtrack_backend.entity.User;
import com.devtrack.devtrack_backend.enums.PlatformRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void persistsUserWithDefaultsAndTimestamps() {
        User saved = userRepository.saveAndFlush(new User("Ada Lovelace", "ada@example.com", "hash"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPlatformRole()).isEqualTo(PlatformRole.USER);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsByEmailIgnoringCase() {
        userRepository.saveAndFlush(new User("Ada", "ada@example.com", "hash"));

        assertThat(userRepository.findByEmailIgnoreCase("ADA@Example.com")).isPresent();
        assertThat(userRepository.existsByEmailIgnoreCase("missing@example.com")).isFalse();
    }

    @Test
    void databaseEnforcesUniqueEmail() {
        userRepository.saveAndFlush(new User("Ada", "ada@example.com", "hash"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("Other", "ada@example.com", "hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updatedAtTriggerAdvancesOnUpdate() {
        User saved = userRepository.saveAndFlush(new User("Ada", "ada@example.com", "hash"));
        jdbc.update("UPDATE users SET full_name = 'Ada L' WHERE id = ?", saved.getId());

        Boolean advanced = jdbc.queryForObject(
                "SELECT updated_at > created_at FROM users WHERE id = ?", Boolean.class, saved.getId());
        assertThat(advanced).isTrue();
    }
}
