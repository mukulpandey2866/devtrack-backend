package com.devtrack.devtrack_backend.repository;

import com.devtrack.devtrack_backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldConnectToDatabaseAndReadUsers() {
        List<User> users = userRepository.findAll();

        assertThat(users).isNotNull();

        System.out.println("Total users: " + users.size());
    }
}
