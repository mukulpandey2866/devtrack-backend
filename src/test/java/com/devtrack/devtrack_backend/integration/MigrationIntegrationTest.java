package com.devtrack.devtrack_backend.integration;

import com.devtrack.devtrack_backend.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves the baseline procedure for the manually created Neon schema without touching any real database. */
class MigrationIntegrationTest extends AbstractIntegrationTest {

    private static final List<String> TABLES = List.of("users", "projects", "project_members", "issues",
            "issue_comments", "issue_assignees", "labels", "issue_labels", "issue_history");

    // Flyway's own history table legitimately adds indexes when baselining.
    private static final String APP_INDEXES =
            "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'";

    @Test
    void emptyDatabaseIsMigratedToTheFullSchema() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class)).isEqualTo(1);
        for (String table : TABLES) {
            assertThat(jdbc.queryForObject("SELECT to_regclass('public." + table + "') IS NOT NULL", Boolean.class))
                    .as(table).isTrue();
        }
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal AND tgfoid = 'set_updated_at'::regproc",
                Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint
                WHERE conrelid = 'issue_labels'::regclass AND contype = 'f' AND confdeltype = 'c'""",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void existingSchemaIsBaselinedWithoutRunningV1OrTouchingData() throws Exception {
        String db = "manual_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + db);
        try {
            executeV1Script(db);
            String userId = insertUser(db);
            int triggersBefore = count(db, "SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal");
            int indexesBefore = count(db, APP_INDEXES);

            MigrateResult result = flyway(db, true).migrate();

            assertThat(result.migrationsExecuted).isZero();
            assertThat(count(db, "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND type = 'BASELINE'"))
                    .isEqualTo(1);
            assertThat(count(db, "SELECT count(*) FROM users WHERE id = '" + userId + "'")).isEqualTo(1);
            assertThat(count(db, "SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal")).isEqualTo(triggersBefore);
            assertThat(count(db, APP_INDEXES)).isEqualTo(indexesBefore);
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + db + " WITH (FORCE)");
        }
    }

    @Test
    void existingSchemaWithoutBaselineIsRefusedAndLeftIntact() throws Exception {
        String db = "manual_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + db);
        try {
            executeV1Script(db);
            insertUser(db);

            assertThatThrownBy(() -> flyway(db, false).migrate()).isInstanceOf(FlywayException.class);

            assertThat(count(db, "SELECT count(*) FROM users")).isEqualTo(1);
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + db + " WITH (FORCE)");
        }
    }

    private Flyway flyway(String db, boolean baselineOnMigrate) {
        return Flyway.configure()
                .dataSource(urlFor(db), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion("1")
                .cleanDisabled(true)
                .load();
    }

    private void executeV1Script(String db) throws Exception {
        String script = StreamUtils.copyToString(
                new ClassPathResource("db/migration/V1__baseline_schema.sql").getInputStream(), StandardCharsets.UTF_8);
        try (Connection connection = connect(db); Statement statement = connection.createStatement()) {
            statement.execute(script);
        }
    }

    private String insertUser(String db) throws SQLException {
        String id = UUID.randomUUID().toString();
        try (Connection connection = connect(db); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (id, full_name, email, password_hash, created_at, updated_at) "
                    + "VALUES ('" + id + "', 'Existing', 'existing@example.com', 'hash', now(), now())");
        }
        return id;
    }

    private int count(String db, String sql) throws SQLException {
        try (Connection connection = connect(db); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private Connection connect(String db) throws SQLException {
        return DriverManager.getConnection(urlFor(db), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private String urlFor(String db) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + db;
    }
}
