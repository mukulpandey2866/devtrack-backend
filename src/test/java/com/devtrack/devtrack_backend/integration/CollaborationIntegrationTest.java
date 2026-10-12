package com.devtrack.devtrack_backend.integration;

import com.devtrack.devtrack_backend.AbstractIntegrationTest;
import com.devtrack.devtrack_backend.support.ApiClient;
import com.devtrack.devtrack_backend.support.ApiClient.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CollaborationIntegrationTest extends AbstractIntegrationTest {

    private TestUser alice;
    private TestUser bob;
    private TestUser carol;
    private TestUser dave;
    private TestUser eve;
    private String projectId;
    private String issueId;

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
        issueId = api.createIssue(alice, projectId, "Shared issue");
    }

    // ---- assignees ----

    @Test
    void assigneesMustBeProjectMembersAndAreRecordedInHistory() throws Exception {
        String path = "/api/issues/" + issueId + "/assignees";

        api.post(carol.token(), path, """
                {"userId":"%s"}""".formatted(carol.id()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").value(carol.id()));
        api.post(carol.token(), path, """
                {"userId":"%s"}""".formatted(bob.id())).andExpect(status().isCreated());
        api.post(carol.token(), path, """
                {"userId":"%s"}""".formatted(carol.id())).andExpect(status().isConflict());
        api.post(carol.token(), path, """
                {"userId":"%s"}""".formatted(eve.id())).andExpect(status().isBadRequest());
        api.post(carol.token(), path, "{}").andExpect(status().isBadRequest());
        api.post(dave.token(), path, """
                {"userId":"%s"}""".formatted(dave.id())).andExpect(status().isForbidden());
        api.post(eve.token(), path, """
                {"userId":"%s"}""".formatted(eve.id())).andExpect(status().isNotFound());

        api.get(dave.token(), path).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        api.get(eve.token(), path).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM issue_history WHERE issue_id = ?::uuid AND field_name = 'assignee' AND new_value IS NOT NULL AND changed_by = ?::uuid",
                Integer.class, issueId, carol.id())).isEqualTo(2);
    }

    @Test
    void assigneeFromAnotherProjectIsRejected() throws Exception {
        TestUser outsider = api.registerAndLogin("Outsider");
        String otherProject = api.createProject(outsider, "OTHER");

        api.post(alice.token(), "/api/issues/" + issueId + "/assignees", """
                {"userId":"%s"}""".formatted(outsider.id())).andExpect(status().isBadRequest());
        assertThat(otherProject).isNotEqualTo(projectId);
    }

    @Test
    void removingAssigneeIsRecordedAndMissingAssignmentIsNotFound() throws Exception {
        api.post(carol.token(), "/api/issues/" + issueId + "/assignees", """
                {"userId":"%s"}""".formatted(carol.id())).andExpect(status().isCreated());

        api.delete(dave.token(), "/api/issues/" + issueId + "/assignees/" + carol.id()).andExpect(status().isForbidden());
        api.delete(carol.token(), "/api/issues/" + issueId + "/assignees/" + carol.id()).andExpect(status().isNoContent());
        api.delete(carol.token(), "/api/issues/" + issueId + "/assignees/" + carol.id()).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM issue_history WHERE issue_id = ?::uuid AND field_name = 'assignee' AND old_value IS NOT NULL AND new_value IS NULL",
                Integer.class, issueId)).isEqualTo(1);
    }

    // ---- labels ----

    @Test
    void labelCatalogueIsManagerOnlyWithValidatedColorsAndUniqueNames() throws Exception {
        String path = "/api/projects/" + projectId + "/labels";

        api.post(bob.token(), path, """
                {"name":" Backend "}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Backend"))
                .andExpect(jsonPath("$.color").value("#808080"));
        api.post(bob.token(), path, """
                {"name":"UI","color":"#1a2b3c"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.color").value("#1A2B3C"));
        api.post(bob.token(), path, """
                {"name":"backend"}""").andExpect(status().isConflict());
        for (String color : new String[]{"red", "#12345", "#GGGGGG", "123456", "#1234567"}) {
            api.post(bob.token(), path, """
                    {"name":"Bad","color":"%s"}""".formatted(color)).andExpect(status().isBadRequest());
        }
        api.post(bob.token(), path, """
                {"name":""}""").andExpect(status().isBadRequest());
        api.post(carol.token(), path, """
                {"name":"Nope"}""").andExpect(status().isForbidden());
        api.post(eve.token(), path, """
                {"name":"Nope"}""").andExpect(status().isNotFound());

        api.get(dave.token(), path)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Backend"))
                .andExpect(jsonPath("$[1].name").value("UI"));
    }

    @Test
    void labelsCanBeRenamedAndDeletedWithIssueHistory() throws Exception {
        String backend = createLabel(bob, "Backend");
        String ui = createLabel(bob, "UI");
        api.put(carol.token(), "/api/issues/" + issueId + "/labels", """
                {"labelIds":["%s"]}""".formatted(backend)).andExpect(status().isOk());

        api.put(bob.token(), "/api/labels/" + backend, """
                {"name":"ui","color":"#000000"}""").andExpect(status().isConflict());
        api.put(carol.token(), "/api/labels/" + backend, """
                {"name":"Server"}""").andExpect(status().isForbidden());
        api.put(bob.token(), "/api/labels/" + backend, """
                {"name":"Server","color":"#00FF00"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Server"));
        api.put(eve.token(), "/api/labels/" + backend, """
                {"name":"Server"}""").andExpect(status().isNotFound());

        api.delete(carol.token(), "/api/labels/" + backend).andExpect(status().isForbidden());
        api.delete(bob.token(), "/api/labels/" + backend).andExpect(status().isNoContent());
        api.delete(bob.token(), "/api/labels/" + backend).andExpect(status().isNotFound());

        api.get(alice.token(), "/api/issues/" + issueId).andExpect(jsonPath("$.labels.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM issue_labels", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM issue_history WHERE issue_id = ?::uuid AND field_name = 'label' AND old_value = 'Server' AND new_value IS NULL",
                Integer.class, issueId)).isEqualTo(1);
        assertThat(ui).isNotBlank();
    }

    @Test
    void issueLabelsAreReplacedAsASetAndStayInsideTheProject() throws Exception {
        String backend = createLabel(bob, "Backend");
        String ui = createLabel(bob, "UI");
        TestUser outsider = api.registerAndLogin("Outsider");
        String otherProject = api.createProject(outsider, "OTHER");
        String foreignLabel = ApiClient.read(api.post(outsider.token(), "/api/projects/" + otherProject + "/labels", """
                {"name":"Foreign"}""").andExpect(status().isCreated()), "$.id");
        String path = "/api/issues/" + issueId + "/labels";

        api.put(carol.token(), path, """
                {"labelIds":["%s","%s"]}""".formatted(backend, ui))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        api.put(carol.token(), path, """
                {"labelIds":["%s"]}""".formatted(ui))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("UI"));
        api.put(carol.token(), path, """
                {"labelIds":["%s"]}""".formatted(foreignLabel)).andExpect(status().isBadRequest());
        api.put(carol.token(), path, """
                {"labelIds":["00000000-0000-0000-0000-000000000000"]}""").andExpect(status().isBadRequest());
        api.put(carol.token(), path, "{}").andExpect(status().isBadRequest());
        api.put(dave.token(), path, """
                {"labelIds":[]}""").andExpect(status().isForbidden());
        api.put(eve.token(), path, """
                {"labelIds":[]}""").andExpect(status().isNotFound());

        api.get(alice.token(), "/api/issues/" + issueId).andExpect(jsonPath("$.labels.length()").value(1));
        api.put(carol.token(), path, """
                {"labelIds":[]}""").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbc.queryForList("SELECT field_name FROM issue_history WHERE issue_id = ?::uuid AND field_name = 'label'",
                String.class, issueId)).hasSize(4);
    }

    @Test
    void databaseCompositeForeignKeyBlocksCrossProjectLabels() throws Exception {
        TestUser outsider = api.registerAndLogin("Outsider");
        String otherProject = api.createProject(outsider, "OTHER");
        String foreignLabel = ApiClient.read(api.post(outsider.token(), "/api/projects/" + otherProject + "/labels", """
                {"name":"Foreign"}""").andExpect(status().isCreated()), "$.id");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO issue_labels (issue_id, label_id, project_id) VALUES (?::uuid, ?::uuid, ?::uuid)",
                issueId, foreignLabel, projectId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- comments ----

    @Test
    void commentsRequireMemberRoleAndNonBlankContent() throws Exception {
        String path = "/api/issues/" + issueId + "/comments";

        api.post(carol.token(), path, """
                {"content":"  Looks good  "}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Looks good"))
                .andExpect(jsonPath("$.author.id").value(carol.id()));
        for (String content : new String[]{"", "   ", "\\n\\t "}) {
            api.post(carol.token(), path, """
                    {"content":"%s"}""".formatted(content)).andExpect(status().isBadRequest());
        }
        api.post(carol.token(), path, "{}").andExpect(status().isBadRequest());
        api.post(carol.token(), path, """
                {"content":"%s"}""".formatted("x".repeat(5001))).andExpect(status().isBadRequest());
        api.post(dave.token(), path, """
                {"content":"viewer comment"}""").andExpect(status().isForbidden());
        api.post(eve.token(), path, """
                {"content":"outsider"}""").andExpect(status().isNotFound());
        api.post(null, path, """
                {"content":"anonymous"}""").andExpect(status().isUnauthorized());
    }

    @Test
    void commentListIsPagedOldestFirstAndReadableByViewers() throws Exception {
        String path = "/api/issues/" + issueId + "/comments";
        for (int i = 1; i <= 3; i++) {
            api.post(carol.token(), path, """
                    {"content":"c%d"}""".formatted(i)).andExpect(status().isCreated());
        }

        api.get(dave.token(), path + "?size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].content").value("c1"))
                .andExpect(jsonPath("$.content[0].author.fullName").value("Carol"));
        api.get(eve.token(), path).andExpect(status().isNotFound());
        api.get(dave.token(), path + "?sort=authorId").andExpect(status().isBadRequest());
    }

    @Test
    void commentOwnershipRulesAreEnforced() throws Exception {
        String commentId = ApiClient.read(api.post(carol.token(), "/api/issues/" + issueId + "/comments", """
                {"content":"mine"}""").andExpect(status().isCreated()), "$.id");
        String path = "/api/comments/" + commentId;

        api.put(alice.token(), path, """
                {"content":"hijack"}""").andExpect(status().isForbidden());
        api.put(bob.token(), path, """
                {"content":"hijack"}""").andExpect(status().isForbidden());
        api.put(eve.token(), path, """
                {"content":"hijack"}""").andExpect(status().isNotFound());
        api.put(carol.token(), path, """
                {"content":"   "}""").andExpect(status().isBadRequest());
        api.put(carol.token(), path, """
                {"content":"edited"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("edited"));

        api.delete(dave.token(), path).andExpect(status().isForbidden());
        api.delete(eve.token(), path).andExpect(status().isNotFound());
        api.delete(bob.token(), path).andExpect(status().isNoContent());
        api.delete(bob.token(), path).andExpect(status().isNotFound());

        String second = ApiClient.read(api.post(carol.token(), "/api/issues/" + issueId + "/comments", """
                {"content":"again"}""").andExpect(status().isCreated()), "$.id");
        api.delete(carol.token(), "/api/comments/" + second).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM issue_comments", Integer.class)).isZero();
    }

    private String createLabel(TestUser actor, String name) throws Exception {
        return ApiClient.read(api.post(actor.token(), "/api/projects/" + projectId + "/labels", """
                {"name":"%s"}""".formatted(name)).andExpect(status().isCreated()), "$.id");
    }
}
