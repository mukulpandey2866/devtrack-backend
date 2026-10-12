package com.devtrack.devtrack_backend.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Thin helper that drives the real HTTP API so tests exercise security, validation and persistence together. */
public class ApiClient {

    public static final String PASSWORD = "S3cure-passw0rd!";

    public record TestUser(String id, String email, String token) {
    }

    private final MockMvc mockMvc;

    public ApiClient(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public ResultActions send(HttpMethod method, String token, String path, String json) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(method, path);
        if (json != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder);
    }

    public ResultActions get(String token, String path) throws Exception {
        return send(HttpMethod.GET, token, path, null);
    }

    public ResultActions post(String token, String path, String json) throws Exception {
        return send(HttpMethod.POST, token, path, json);
    }

    public ResultActions put(String token, String path, String json) throws Exception {
        return send(HttpMethod.PUT, token, path, json);
    }

    public ResultActions patch(String token, String path, String json) throws Exception {
        return send(HttpMethod.PATCH, token, path, json);
    }

    public ResultActions delete(String token, String path) throws Exception {
        return send(HttpMethod.DELETE, token, path, null);
    }

    public static <T> T read(ResultActions result, String jsonPath) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), jsonPath);
    }

    public TestUser registerAndLogin(String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        post(null, "/api/auth/register", """
                {"fullName":"%s","email":"%s","password":"%s"}""".formatted(name, email, PASSWORD))
                .andExpect(status().isCreated());
        ResultActions login = post(null, "/api/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD)).andExpect(status().isOk());
        return new TestUser(read(login, "$.user.id"), email, read(login, "$.accessToken"));
    }

    public String createProject(TestUser owner, String key) throws Exception {
        ResultActions created = post(owner.token(), "/api/projects", """
                {"name":"Project %s","projectKey":"%s","description":"d"}""".formatted(key, key))
                .andExpect(status().isCreated());
        return read(created, "$.id");
    }

    public void addMember(TestUser actor, String projectId, TestUser user, String role) throws Exception {
        post(actor.token(), "/api/projects/" + projectId + "/members", """
                {"email":"%s","role":"%s"}""".formatted(user.email(), role)).andExpect(status().isCreated());
    }

    public String createIssue(TestUser actor, String projectId, String title) throws Exception {
        ResultActions created = post(actor.token(), "/api/projects/" + projectId + "/issues", """
                {"title":"%s","issueType":"TASK"}""".formatted(title)).andExpect(status().isCreated());
        return read(created, "$.id");
    }
}
