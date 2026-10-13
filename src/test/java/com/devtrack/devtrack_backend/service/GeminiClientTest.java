package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.config.GeminiProperties;
import com.devtrack.devtrack_backend.exception.AiServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiClientTest {

    private static final String API_KEY = "test-key-do-not-leak";
    private static final String URL = "https://gemini.test/v1beta/models/test-model:generateContent";
    private static final String FALLBACK_URL = "https://gemini.test/v1beta/models/fallback-model:generateContent";

    private MockRestServiceServer server;

    private GeminiClient client(String apiKey) {
        return client(apiKey, "");
    }

    private GeminiClient client(String apiKey, String fallbackModel) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        server = MockRestServiceServer.bindTo(builder).build();
        GeminiProperties properties = new GeminiProperties(apiKey, "test-model", fallbackModel, "https://gemini.test",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 2048);
        return new GeminiClient(builder.build(), properties, JsonMapper.builder().build(), Duration.ZERO);
    }

    private String generate(GeminiClient client) {
        return client.generateJson("system rules", "user content", Map.of("type", "object"));
    }

    private static String candidate(String text, String finishReason) {
        return """
                {"candidates":[{"content":{"role":"model","parts":[{"text":%s}]},"finishReason":"%s"}]}"""
                .formatted(JsonMapper.builder().build().writeValueAsString(text), finishReason);
    }

    private AiServiceException failure(GeminiClient client) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> generate(client));
        assertThat(thrown).isInstanceOf(AiServiceException.class);
        assertThat(thrown.getMessage()).doesNotContain(API_KEY);
        return (AiServiceException) thrown;
    }

    @Test
    void sendsStructuredRequestWithKeyInHeaderAndReturnsModelText() {
        GeminiClient client = client(API_KEY);
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", API_KEY))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"responseMimeType\":\"application/json\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("system rules")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(API_KEY))))
                .andRespond(withSuccess(candidate("{\"title\":\"t\"}", "STOP"), org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(generate(client)).isEqualTo("{\"title\":\"t\"}");
        server.verify();
    }

    @Test
    void ignoresThoughtParts() {
        GeminiClient client = client(API_KEY);
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"candidates":[{"content":{"parts":[{"text":"thinking...","thought":true},{"text":"{\\"a\\":1}"}]},"finishReason":"STOP"}]}""",
                org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(generate(client)).isEqualTo("{\"a\":1}");
    }

    @Test
    void missingApiKeyFailsWithoutCallingProvider() {
        GeminiClient client = client("");

        AiServiceException ex = failure(client);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void rateLimitMapsTo429() {
        GeminiClient client = client(API_KEY);
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("{\"error\":{\"message\":\"quota details with " + API_KEY + "\"}}"));

        assertThat(failure(client).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void invalidKeyOrUnknownModelMapsTo503() {
        for (HttpStatus status : new HttpStatus[]{HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND}) {
            GeminiClient client = client(API_KEY);
            server.expect(requestTo(URL)).andRespond(withStatus(status).body("API key not valid"));

            assertThat(failure(client).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Test
    void providerServerErrorMapsTo503AndBadRequestTo502() {
        GeminiClient client = client(API_KEY);
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThat(failure(client).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        client = client(API_KEY);
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("raw provider detail"));
        AiServiceException ex = failure(client);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.getMessage()).doesNotContain("raw provider detail");
    }

    @Test
    void overloadedProviderIsRetriedOnceThenSucceeds() {
        GeminiClient client = client(API_KEY);
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withSuccess(candidate("{\"ok\":true}", "STOP"),
                org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(generate(client)).isEqualTo("{\"ok\":true}");
        server.verify();
    }

    @Test
    void persistentOverloadStopsAfterOneRetry() {
        GeminiClient client = client(API_KEY);
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(failure(client).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void overloadedModelFallsBackToTheFallbackModel() {
        GeminiClient client = client(API_KEY, "fallback-model");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(FALLBACK_URL)).andRespond(withSuccess(candidate("{\"ok\":true}", "STOP"),
                org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(generate(client)).isEqualTo("{\"ok\":true}");
        server.verify();
    }

    @Test
    void timeoutFallsBackToTheFallbackModelButIsNotRetriedWithoutOne() {
        GeminiClient client = client(API_KEY, "fallback-model");
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(requestTo(FALLBACK_URL)).andRespond(withSuccess(candidate("{\"ok\":true}", "STOP"),
                org.springframework.http.MediaType.APPLICATION_JSON));
        assertThat(generate(client)).isEqualTo("{\"ok\":true}");
        server.verify();

        // No fallback configured: a single attempt, then 504.
        GeminiClient noFallback = client(API_KEY);
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertThat(failure(noFallback).getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        server.verify();
    }

    @Test
    void fallbackIsNotUsedForRateLimitsAndItsFailureIsMapped() {
        GeminiClient client = client(API_KEY, "fallback-model");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(failure(client).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        server.verify();

        client = client(API_KEY, "fallback-model");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(FALLBACK_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(failure(client).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void timeoutMapsTo504() {
        GeminiClient client = client(API_KEY);
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThat(failure(client).getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void networkFailureMapsTo503() {
        GeminiClient client = client(API_KEY);
        server.expect(requestTo(URL)).andRespond(withException(new ConnectException("Connection refused")));

        assertThat(failure(client).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void blockedPromptAndBlockedCandidateMapTo422() {
        GeminiClient client = client(API_KEY);
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"promptFeedback":{"blockReason":"SAFETY"}}""", org.springframework.http.MediaType.APPLICATION_JSON));
        assertThat(failure(client).getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        client = client(API_KEY);
        server.expect(requestTo(URL)).andRespond(withSuccess(candidate("x", "SAFETY"), org.springframework.http.MediaType.APPLICATION_JSON));
        assertThat(failure(client).getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void unusableProviderResponsesMapTo502() {
        String[] bodies = {
                "not json",
                "{}",
                "{\"candidates\":[]}",
                candidate("cut off", "MAX_TOKENS"),
                "{\"candidates\":[{\"content\":{\"parts\":[]},\"finishReason\":\"STOP\"}]}"
        };
        for (String body : bodies) {
            GeminiClient client = client(API_KEY);
            server.expect(requestTo(URL)).andRespond(withSuccess(body, org.springframework.http.MediaType.APPLICATION_JSON));

            assertThat(failure(client).getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        }
    }

    @Test
    void propertiesNeverPrintTheApiKey() {
        GeminiProperties properties = new GeminiProperties(API_KEY, null, null, null, null, null, 0);

        assertThat(properties.toString()).doesNotContain(API_KEY);
        assertThat(properties.model()).isEqualTo(GeminiProperties.DEFAULT_MODEL);
        assertThatThrownBy(() -> new GeminiProperties(API_KEY, "bad/model", null, null, null, null, 0))
                .isInstanceOf(IllegalStateException.class);
    }
}
