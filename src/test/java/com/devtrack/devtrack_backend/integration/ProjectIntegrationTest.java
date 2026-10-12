package com.devtrack.devtrack_backend.integration;

import com.devtrack.devtrack_backend.AbstractIntegrationTest;
import com.devtrack.devtrack_backend.support.ApiClient.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectIntegrationTest extends AbstractIntegrationTest {

    private TestUser alice;
    private TestUser bob;
    private TestUser carol;
    private TestUser dave;
    private TestUser eve;
    private String projectId;

    @BeforeEach
    void setUpUsers() throws Exception {
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

    private String membersPath() {
        return "/api/projects/" + projectId + "/members";
    }

    private String roleOf(TestUser user) {
        return jdbc.queryForObject("SELECT role FROM project_members WHERE project_id = ?::uuid AND user_id = ?::uuid",
                String.class, projectId, user.id());
    }

    @Test
    void creatorBecomesOwnerAndOwnerMember() throws Exception {
        api.post(alice.token(), "/api/projects", """
                {"name":"Second","projectKey":"abc","description":"  hello "}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectKey").value("ABC"))
                .andExpect(jsonPath("$.ownerId").value(alice.id()))
                .andExpect(jsonPath("$.currentUserRole").value("OWNER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.description").value("hello"));

        api.get(alice.token(), membersPath())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user.id").value(alice.id()))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].user.passwordHash").doesNotExist());
    }

    @Test
    void invalidAndDuplicateKeysAreRejected() throws Exception {
        for (String key : new String[]{"1ABC", "A", "TOOLONGKEY123", "AB-C", ""}) {
            api.post(alice.token(), "/api/projects", """
                    {"name":"X","projectKey":"%s"}""".formatted(key)).andExpect(status().isBadRequest());
        }
        api.post(eve.token(), "/api/projects", """
                {"name":"Dup","projectKey":"proj"}""").andExpect(status().isConflict());
    }

    @Test
    void nonMembersCannotDiscoverProject() throws Exception {
        api.get(eve.token(), "/api/projects/" + projectId).andExpect(status().isNotFound());
        api.put(eve.token(), "/api/projects/" + projectId, """
                {"name":"Hacked"}""").andExpect(status().isNotFound());
        api.delete(eve.token(), "/api/projects/" + projectId).andExpect(status().isNotFound());
        api.get(eve.token(), membersPath()).andExpect(status().isNotFound());
        api.get(eve.token(), "/api/projects/" + projectId + "/issues").andExpect(status().isNotFound());
        api.get(eve.token(), "/api/projects/" + projectId + "/labels").andExpect(status().isNotFound());
        api.get(eve.token(), "/api/projects")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listIsPaginatedAndLimitedToAccessibleProjects() throws Exception {
        api.createProject(alice, "SECOND");
        api.createProject(alice, "THIRD");

        api.get(alice.token(), "/api/projects?size=2&sort=name,asc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Project PROJ"));
        api.get(dave.token(), "/api/projects")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].currentUserRole").value("VIEWER"));
        api.get(alice.token(), "/api/projects?size=100000")
                .andExpect(jsonPath("$.size").value(100));
        api.get(alice.token(), "/api/projects?sort=password,asc").andExpect(status().isBadRequest());
        api.get(alice.token(), "/api/projects?status=NOPE").andExpect(status().isBadRequest());
    }

    @Test
    void onlyManagersAndOwnersCanUpdateAndKeyStaysImmutable() throws Exception {
        String body = """
                {"name":"Renamed","description":"new","projectKey":"HACK"}""";
        api.put(carol.token(), "/api/projects/" + projectId, body).andExpect(status().isForbidden());
        api.put(dave.token(), "/api/projects/" + projectId, body).andExpect(status().isForbidden());
        api.put(bob.token(), "/api/projects/" + projectId, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.projectKey").value("PROJ"));
    }

    @Test
    void archiveIsOwnerOnlyAndMakesProjectReadOnly() throws Exception {
        api.delete(bob.token(), "/api/projects/" + projectId).andExpect(status().isForbidden());
        api.delete(alice.token(), "/api/projects/" + projectId).andExpect(status().isNoContent());
        api.delete(alice.token(), "/api/projects/" + projectId).andExpect(status().isNoContent());

        api.get(carol.token(), "/api/projects/" + projectId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        api.post(carol.token(), "/api/projects/" + projectId + "/issues", """
                {"title":"Late","issueType":"TASK"}""").andExpect(status().isConflict());
        api.post(alice.token(), membersPath(), """
                {"email":"%s","role":"MEMBER"}""".formatted(eve.email())).andExpect(status().isConflict());
        api.put(bob.token(), "/api/projects/" + projectId, """
                {"name":"Edit","status":"ACTIVE"}""").andExpect(status().isForbidden());
        api.put(alice.token(), "/api/projects/" + projectId, """
                {"name":"Back","status":"ACTIVE"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void addingMembersFollowsRoleHierarchy() throws Exception {
        api.post(bob.token(), membersPath(), """
                {"email":"%s","role":"MEMBER"}""".formatted(eve.email())).andExpect(status().isCreated());
        api.post(bob.token(), membersPath(), """
                {"email":"%s","role":"MANAGER"}""".formatted(api.registerAndLogin("Zed").email()))
                .andExpect(status().isForbidden());
        api.post(bob.token(), membersPath(), """
                {"email":"%s","role":"OWNER"}""".formatted(api.registerAndLogin("Yan").email()))
                .andExpect(status().isForbidden());
        api.post(carol.token(), membersPath(), """
                {"email":"%s","role":"VIEWER"}""".formatted(api.registerAndLogin("Xia").email()))
                .andExpect(status().isForbidden());
        api.post(dave.token(), membersPath(), """
                {"email":"x@example.com","role":"VIEWER"}""").andExpect(status().isForbidden());
        api.post(alice.token(), membersPath(), """
                {"email":"%s","role":"MEMBER"}""".formatted(carol.email())).andExpect(status().isConflict());
        api.post(alice.token(), membersPath(), """
                {"email":"ghost@example.com","role":"MEMBER"}""").andExpect(status().isNotFound());
        api.post(alice.token(), membersPath(), """
                {"email":"%s","role":"SUPERUSER"}""".formatted(eve.email())).andExpect(status().isBadRequest());
    }

    @Test
    void roleChangesAreRestrictedByHierarchy() throws Exception {
        api.put(bob.token(), membersPath() + "/" + carol.id(), """
                {"role":"MANAGER"}""").andExpect(status().isForbidden());
        api.put(carol.token(), membersPath() + "/" + carol.id(), """
                {"role":"OWNER"}""").andExpect(status().isForbidden());
        api.put(carol.token(), membersPath() + "/" + dave.id(), """
                {"role":"MEMBER"}""").andExpect(status().isForbidden());
        api.put(bob.token(), membersPath() + "/" + alice.id(), """
                {"role":"VIEWER"}""").andExpect(status().isForbidden());
        api.put(bob.token(), membersPath() + "/" + dave.id(), """
                {"role":"MEMBER"}""").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MEMBER"));
        api.put(alice.token(), membersPath() + "/" + carol.id(), """
                {"role":"MANAGER"}""").andExpect(status().isOk());
        api.put(eve.token(), membersPath() + "/" + carol.id(), """
                {"role":"VIEWER"}""").andExpect(status().isNotFound());
        assertThat(roleOf(carol)).isEqualTo("MANAGER");
    }

    @Test
    void lastOwnerCannotBeDemotedOrRemovedAndPrimaryOwnerIsReassigned() throws Exception {
        api.put(alice.token(), membersPath() + "/" + alice.id(), """
                {"role":"MANAGER"}""").andExpect(status().isConflict());
        api.delete(alice.token(), membersPath() + "/" + alice.id()).andExpect(status().isConflict());

        api.put(alice.token(), membersPath() + "/" + bob.id(), """
                {"role":"OWNER"}""").andExpect(status().isOk());
        api.put(alice.token(), membersPath() + "/" + alice.id(), """
                {"role":"MEMBER"}""").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT owner_id::text FROM projects WHERE id = ?::uuid", String.class, projectId))
                .isEqualTo(bob.id());
        api.delete(bob.token(), membersPath() + "/" + bob.id()).andExpect(status().isConflict());
    }

    @Test
    void membersCanLeaveAndManagersCanRemoveLowerRolesOnly() throws Exception {
        api.delete(bob.token(), membersPath() + "/" + alice.id()).andExpect(status().isForbidden());
        api.delete(carol.token(), membersPath() + "/" + dave.id()).andExpect(status().isForbidden());
        api.delete(bob.token(), membersPath() + "/" + dave.id()).andExpect(status().isNoContent());
        api.delete(carol.token(), membersPath() + "/" + carol.id()).andExpect(status().isNoContent());

        api.get(carol.token(), "/api/projects/" + projectId).andExpect(status().isNotFound());
        api.delete(alice.token(), membersPath() + "/" + eve.id()).andExpect(status().isNotFound());
    }

    @Test
    void removingMemberClearsTheirAssignmentsAndRecordsHistory() throws Exception {
        String issueId = api.createIssue(alice, projectId, "Assigned work");
        api.post(alice.token(), "/api/issues/" + issueId + "/assignees", """
                {"userId":"%s"}""".formatted(carol.id())).andExpect(status().isCreated());

        api.delete(alice.token(), membersPath() + "/" + carol.id()).andExpect(status().isNoContent());

        api.get(alice.token(), "/api/issues/" + issueId + "/assignees")
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM issue_history WHERE issue_id = ?::uuid AND field_name = 'assignee' AND new_value IS NULL",
                Integer.class, issueId)).isEqualTo(1);
    }
}
