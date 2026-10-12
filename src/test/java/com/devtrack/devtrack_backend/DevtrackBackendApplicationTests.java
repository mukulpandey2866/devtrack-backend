package com.devtrack.devtrack_backend;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevtrackBackendApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoadsWithSchemaValidation() {
        // Context startup runs Flyway and Hibernate ddl-auto=validate against PostgreSQL.
    }

    @Test
    void healthEndpointIsPublicAndDoesNotLeakDetails() throws Exception {
        api.get(null, "/actuator/health")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void livenessAndReadinessProbesArePublic() throws Exception {
        api.get(null, "/actuator/health/liveness").andExpect(status().isOk());
        api.get(null, "/actuator/health/readiness").andExpect(status().isOk());
    }

    @Test
    void sensitiveActuatorEndpointsAreNotExposed() throws Exception {
        api.get(null, "/actuator/env").andExpect(status().isUnauthorized());
        api.get(null, "/actuator/heapdump").andExpect(status().isUnauthorized());
        api.get(null, "/actuator/configprops").andExpect(status().isUnauthorized());
    }
}
