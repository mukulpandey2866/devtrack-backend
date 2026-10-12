package com.devtrack.devtrack_backend.integration;

import com.devtrack.devtrack_backend.AbstractIntegrationTest;
import com.devtrack.devtrack_backend.support.ApiClient;
import com.devtrack.devtrack_backend.support.ApiClient.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IssueIntegrationTest extends AbstractIntegrationTest {

    private TestUser alice;
    private TestUser bob;
    private TestUser carol;
    private TestUser dave;
    private TestUser eve;
    private String projectId;

    @BeforeEach
    void setUpProject() throws Exception {
        alice = api.registerAndLogin("Alice");
        bob = api.registerAndLogin("Bob");
        carol = api.registerAndLogin("Carol");
        dave = api.registerAndLogin("Dave");
        eve = api.registerAndLogin("Eve");
        projectId = api.createProject(alice, "PROJ");
        api.addMember(alice, projectId, bob, "MANAGER");
        api.addMember(alice, projectId, carol, "MEMBER");
        api.addMember(alice, projectId, dave, "VIEWER");
    }

    private String issuesPath() {
        return "/api/projects/" + projectId + "/issues";
    }

    @Test
    void creatorIsReporterAndClientCannotChooseReporter() throws Exception {
        api.post(carol.token(), issuesPath(), """
                {"title":"  First issue ","description":"d","issueType":"BUG","reporterId":"%s","status":"DONE"}"""
                .formatted(alice.id()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issueKey").value("PROJ-1"))
                .andExpect(jsonPath("$.issueNumber").value(1))
                .andExpect(jsonPath("$.title").value("First issue"))
                .andExpect(jsonPath("$.reporter.id").value(carol.id()))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.assignees.length()").value(0))
                .andExpect(jsonPath("$.labels.length()").value(0));
    }

    @Test
    void issueNumbersAreSequentialPerProject() throws Exception {
        String otherProject = api.createProject(alice, "OTHER");
        for (int expected = 1; expected <= 3; expected++) {
            api.post(alice.token(), issuesPath(), """
                    {"title":"T%d","issueType":"TASK"}""".formatted(expected))
                    .andExpect(jsonPath("$.issueNumber").value(expected));
        }
        api.post(alice.token(), "/api/projects/" + otherProject + "/issues", """
                {"title":"Other","issueType":"TASK"}""").andExpect(jsonPath("$.issueKey").value("OTHER-1"));
    }

    @Test
    void concurrentCreationNeverProducesDuplicateOrSkippedNumbers() throws Exception {
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int n = i;
            TestUser creator = i % 2 == 0 ? alice : carol;
            futures.add(pool.submit(() -> {
                start.await();
                return ApiClient.<Integer>read(api.post(creator.token(), issuesPath(), """
                        {"title":"Concurrent %d","issueType":"TASK"}""".formatted(n))
                        .andExpect(status().isCreated()), "$.issueNumber");
            }));
        }
        start.countDown();
        Set<Integer> numbers = new HashSet<>();
        for (Future<Integer> future : futures) {
            numbers.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(numbers).hasSize(threads);
        assertThat(numbers).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    }

    @Test
    void writePermissionsAndValidationAreEnforced() throws Exception {
        String valid = """
                {"title":"Ok","issueType":"TASK"}""";
        api.post(dave.token(), issuesPath(), valid).andExpect(status().isForbidden());
        api.post(eve.token(), issuesPath(), valid).andExpect(status().isNotFound());
        api.post(null, issuesPath(), valid).andExpect(status().isUnauthorized());

        String[] invalid = {
                """
                {"title":"","issueType":"TASK"}""",
                """
                {"title":"x","issueType":"BUG2"}""",
                """
                {"issueType":"TASK"}""",
                """
                {"title":"x","issueType":"TASK","priority":"URGENT"}""",
                """
                {"title":"x","issueType":"TASK","estimatedHours":-1}""",
                """
                {"title":"x","issueType":"TASK","estimatedHours":1.234}""",
                """
                {"title":"x","issueType":"TASK","dueDate":"2000-01-01"}""",
                """
                {"title":"x","issueType":"TASK","dueDate":"not-a-date"}""",
                """
                {"title":"%s","issueType":"TASK"}""".formatted("t".repeat(201))
        };
        for (String body : invalid) {
            api.post(alice.token(), issuesPath(), body).andExpect(status().isBadRequest());
        }
    }

    @Test
    void updateRecordsOnlyChangedFieldsInHistoryWithActor() throws Exception {
        String issueId = api.createIssue(alice, projectId, "Original");
        String due = LocalDate.now().plusDays(10).toString();

        api.put(carol.token(), "/api/issues/" + issueId, """
                {"title":"Renamed","description":"details","issueType":"TASK","priority":"HIGH","dueDate":"%s","estimatedHours":4.5}"""
                .formatted(due))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.estimatedHours").value(4.5))
                .andExpect(jsonPath("$.dueDate").value(due))
                .andExpect(jsonPath("$.reporter.id").value(alice.id()));

        assertThat(historyFields(issueId)).containsExactlyInAnyOrder(
                "created", "title", "description", "priority", "dueDate", "estimatedHours");
        assertThat(jdbc.queryForObject(
                "SELECT old_value || '>' || new_value FROM issue_history WHERE issue_id = ?::uuid AND field_name = 'priority'",
                String.class, issueId)).isEqualTo("MEDIUM>HIGH");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM issue_history WHERE issue_id = ?::uuid AND field_name <> 'created' AND changed_by = ?::uuid",
                Integer.class, issueId, carol.id())).isEqualTo(5);

        api.put(carol.token(), "/api/issues/" + issueId, """
                {"title":"Renamed","description":"details","issueType":"TASK","priority":"HIGH","dueDate":"%s","estimatedHours":4.50}"""
                .formatted(due)).andExpect(status().isOk());
        assertThat(historyFields(issueId)).hasSize(6);
    }

    @Test
    void statusChangesAreRestrictedAndRecorded() throws Exception {
        String issueId = api.createIssue(alice, projectId, "Workflow");

        api.patch(dave.token(), "/api/issues/" + issueId + "/status", """
                {"status":"DONE"}""").andExpect(status().isForbidden());
        api.patch(eve.token(), "/api/issues/" + issueId + "/status", """
                {"status":"DONE"}""").andExpect(status().isNotFound());
        api.patch(carol.token(), "/api/issues/" + issueId + "/status", """
                {"status":"FINISHED"}""").andExpect(status().isBadRequest());
        api.patch(carol.token(), "/api/issues/" + issueId + "/status", """
                {"status":"IN_PROGRESS"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        api.get(dave.token(), "/api/issues/" + issueId + "/history?sort=changedAt,desc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fieldName").value("status"))
                .andExpect(jsonPath("$.content[0].oldValue").value("TODO"))
                .andExpect(jsonPath("$.content[0].newValue").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.content[0].changedBy.id").value(carol.id()));
    }

    @Test
    void historyCannotBeModifiedThroughTheApi() throws Exception {
        String issueId = api.createIssue(alice, projectId, "Audited");
        String path = "/api/issues/" + issueId + "/history";

        api.post(alice.token(), path, "{}").andExpect(status().isMethodNotAllowed());
        api.put(alice.token(), path, "{}").andExpect(status().isMethodNotAllowed());
        api.delete(alice.token(), path).andExpect(status().isMethodNotAllowed());
        api.get(eve.token(), path).andExpect(status().isNotFound());
    }

    @Test
    void filtersPaginationAndSortingWork() throws Exception {
        LocalDate soon = LocalDate.now().plusDays(3);
        LocalDate later = LocalDate.now().plusDays(30);
        String bug = createDetailed(alice, "Login bug", "BUG", "CRITICAL", soon);
        String story = createDetailed(carol, "Checkout story", "STORY", "LOW", later);
        String task = createDetailed(carol, "Refactor task", "TASK", "MEDIUM", null);
        api.patch(alice.token(), "/api/issues/" + bug + "/status", """
                {"status":"DONE"}""").andExpect(status().isOk());
        api.post(alice.token(), "/api/issues/" + story + "/assignees", """
                {"userId":"%s"}""".formatted(carol.id())).andExpect(status().isCreated());
        String label = ApiClient.read(api.post(bob.token(), "/api/projects/" + projectId + "/labels", """
                {"name":"urgent","color":"#FF0000"}""").andExpect(status().isCreated()), "$.id");
        api.put(carol.token(), "/api/issues/" + task + "/labels", """
                {"labelIds":["%s"]}""".formatted(label)).andExpect(status().isOk());

        assertTotal("", 3);
        assertTotal("?status=DONE", 1);
        assertTotal("?status=DONE&status=TODO", 3);
        assertTotal("?priority=LOW&priority=MEDIUM", 2);
        assertTotal("?type=BUG", 1);
        assertTotal("?reporterId=" + carol.id(), 2);
        assertTotal("?assigneeId=" + carol.id(), 1);
        assertTotal("?labelId=" + label, 1);
        assertTotal("?dueBefore=" + soon.plusDays(1), 1);
        assertTotal("?dueAfter=" + soon.plusDays(1), 1);
        assertTotal("?q=CHECKOUT", 1);
        assertTotal("?q=%25", 0);
        api.get(alice.token(), issuesPath() + "?dueAfter=" + later + "&dueBefore=" + soon).andExpect(status().isBadRequest());
        api.get(alice.token(), issuesPath() + "?status=BOGUS").andExpect(status().isBadRequest());
        api.get(alice.token(), issuesPath() + "?sort=reporterId").andExpect(status().isBadRequest());

        api.get(alice.token(), issuesPath() + "?size=2&page=0&sort=issueNumber,asc")
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Login bug"))
                .andExpect(jsonPath("$.content[0].reporter.id").value(alice.id()))
                .andExpect(jsonPath("$.totalPages").value(2));
        api.get(alice.token(), issuesPath() + "?size=2&page=1&sort=issueNumber,asc")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].labels[0].name").value("urgent"));
        api.get(alice.token(), issuesPath() + "?size=5000").andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void onlyManagersCanDeleteAndDeletionRemovesDependentRows() throws Exception {
        String issueId = api.createIssue(alice, projectId, "Doomed");
        api.post(carol.token(), "/api/issues/" + issueId + "/comments", """
                {"content":"hello"}""").andExpect(status().isCreated());
        api.post(carol.token(), "/api/issues/" + issueId + "/assignees", """
                {"userId":"%s"}""".formatted(carol.id())).andExpect(status().isCreated());
        String label = ApiClient.read(api.post(bob.token(), "/api/projects/" + projectId + "/labels", """
                {"name":"bug"}""").andExpect(status().isCreated()), "$.id");
        api.put(carol.token(), "/api/issues/" + issueId + "/labels", """
                {"labelIds":["%s"]}""".formatted(label)).andExpect(status().isOk());

        api.delete(carol.token(), "/api/issues/" + issueId).andExpect(status().isForbidden());
        api.delete(eve.token(), "/api/issues/" + issueId).andExpect(status().isNotFound());
        api.delete(bob.token(), "/api/issues/" + issueId).andExpect(status().isNoContent());

        api.get(alice.token(), "/api/issues/" + issueId).andExpect(status().isNotFound());
        for (String table : List.of("issue_comments", "issue_assignees", "issue_labels", "issue_history")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM labels", Integer.class)).isEqualTo(1);
    }

    @Test
    void malformedIdentifiersReturnBadRequest() throws Exception {
        api.get(alice.token(), "/api/issues/not-a-uuid").andExpect(status().isBadRequest());
        api.get(alice.token(), "/api/issues/00000000-0000-0000-0000-000000000000").andExpect(status().isNotFound());
    }

    private List<String> historyFields(String issueId) {
        return jdbc.queryForList("SELECT field_name FROM issue_history WHERE issue_id = ?::uuid", String.class, issueId);
    }

    private String createDetailed(TestUser actor, String title, String type, String priority, LocalDate due) throws Exception {
        String dueJson = due == null ? "null" : "\"" + due + "\"";
        return ApiClient.read(api.post(actor.token(), issuesPath(), """
                {"title":"%s","issueType":"%s","priority":"%s","dueDate":%s}"""
                .formatted(title, type, priority, dueJson)).andExpect(status().isCreated()), "$.id");
    }

    private void assertTotal(String query, int expected) throws Exception {
        api.get(alice.token(), issuesPath() + query)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(expected));
    }
}
