package com.devtrack.devtrack_backend.integration;

import com.devtrack.devtrack_backend.AbstractIntegrationTest;
import com.devtrack.devtrack_backend.exception.AiServiceException;
import com.devtrack.devtrack_backend.service.GeminiClient;
import com.devtrack.devtrack_backend.support.ApiClient.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Gemini client is mocked: no API key or network access is needed. */
class AiIssueDraftIntegrationTest extends AbstractIntegrationTest {

    private static final String PATH = "/api/ai/issues/draft";
    private static final String MODEL_OUTPUT = """
            {"title":"Handle repeated invalid login attempts safely",
             "description":"Investigate and resolve crashes after repeated failed login attempts.",
             "acceptanceCriteria":["Invalid credentials display an error message.","Valid login continues to work."],
             "suggestedLabels":["bug","authentication"]}""";

    @MockitoBean
    private GeminiClient geminiClient;

    private TestUser owner;
    private TestUser member;
    private TestUser viewer;
    private TestUser outsider;
    private String projectId;

    @BeforeEach
    void setUpProject() throws Exception {
        owner = api.registerAndLogin("Owner");
        member = api.registerAndLogin("Member");
        viewer = api.registerAndLogin("Viewer");
        outsider = api.registerAndLogin("Outsider");
        projectId = api.createProject(owner, "AIP");
        api.addMember(owner, projectId, member, "MEMBER");
        api.addMember(owner, projectId, viewer, "VIEWER");
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private String body(String projectIdValue, String title, String description) {
        return """
                {"projectId":%s,"title":"%s","description":"%s"}""".formatted(
                projectIdValue == null ? "null" : "\"" + projectIdValue + "\"", title, description);
    }

    @Test
    void requiresAuthentication() throws Exception {
        api.post(null, PATH, body(projectId, "", "Login crashes")).andExpect(status().isUnauthorized());
        verify(geminiClient, never()).generateJson(anyString(), anyString(), anyMap());
    }

    @Test
    void memberReceivesDraftAndNothingIsPersisted() throws Exception {
        when(geminiClient.generateJson(anyString(), anyString(), anyMap())).thenReturn(MODEL_OUTPUT);
        long issues = count("issues");
        long labels = count("labels");
        long history = count("issue_history");

        api.post(member.token(), PATH, body(projectId, "", "Login sometimes crashes after repeated invalid passwords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Handle repeated invalid login attempts safely"))
                .andExpect(jsonPath("$.description").isNotEmpty())
                .andExpect(jsonPath("$.acceptanceCriteria.length()").value(2))
                .andExpect(jsonPath("$.suggestedLabels[0]").value("bug"));

        assertThat(count("issues")).isEqualTo(issues);
        assertThat(count("labels")).isEqualTo(labels);
        assertThat(count("issue_history")).isEqualTo(history);
        assertThat(count("issue_labels")).isZero();
        assertThat(count("issue_assignees")).isZero();
    }

    @Test
    void projectIdIsOptional() throws Exception {
        when(geminiClient.generateJson(anyString(), anyString(), anyMap())).thenReturn(MODEL_OUTPUT);

        api.post(outsider.token(), PATH, body(null, "Some title", "")).andExpect(status().isOk());
    }

    @Test
    void invalidInputIsRejectedWithValidationProblem() throws Exception {
        api.post(member.token(), PATH, body(projectId, "  ", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
        api.post(member.token(), PATH, body(projectId, "x".repeat(201), "d"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        api.post(member.token(), PATH, body(projectId, "t", "x".repeat(5001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        api.post(member.token(), PATH, """
                {"projectId":"not-a-uuid","title":"t"}""").andExpect(status().isBadRequest());
        api.post(member.token(), PATH, "{").andExpect(status().isBadRequest());

        verify(geminiClient, never()).generateJson(anyString(), anyString(), anyMap());
    }

    @Test
    void projectAccessFollowsExistingRules() throws Exception {
        api.post(outsider.token(), PATH, body(projectId, "t", "d")).andExpect(status().isNotFound());
        api.post(member.token(), PATH, body(java.util.UUID.randomUUID().toString(), "t", "d"))
                .andExpect(status().isNotFound());
        api.post(viewer.token(), PATH, body(projectId, "t", "d")).andExpect(status().isForbidden());

        verify(geminiClient, never()).generateJson(anyString(), anyString(), anyMap());
    }

    @Test
    void providerFailuresUseProblemFormatWithoutLeakingDetails() throws Exception {
        when(geminiClient.generateJson(anyString(), anyString(), anyMap()))
                .thenThrow(new AiServiceException(HttpStatus.TOO_MANY_REQUESTS, "The AI provider rate limit or quota has been reached; try again later"));
        api.post(member.token(), PATH, body(projectId, "t", "d"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").isNotEmpty());

        doThrow(new AiServiceException(HttpStatus.GATEWAY_TIMEOUT, "The AI provider took too long to respond"))
                .when(geminiClient).generateJson(anyString(), anyString(), anyMap());
        api.post(member.token(), PATH, body(projectId, "t", "d")).andExpect(status().isGatewayTimeout());

        doReturn("this is not json").when(geminiClient).generateJson(anyString(), anyString(), anyMap());
        api.post(member.token(), PATH, body(projectId, "t", "d"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("this is not json"))));
    }
}
