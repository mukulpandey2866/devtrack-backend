package com.devtrack.devtrack_backend.service;

import com.devtrack.devtrack_backend.config.GeminiProperties;
import com.devtrack.devtrack_backend.exception.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal Gemini generateContent client. It only returns the model's JSON text; callers must validate it.
 * Prompts, responses and the API key are never logged, and provider error bodies are never passed on.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final Set<String> CONTENT_BLOCKED = Set.of("SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "RECITATION");

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final JsonMapper jsonMapper;

    private final Duration retryDelay;

    @Autowired
    public GeminiClient(RestClient geminiRestClient, GeminiProperties properties, JsonMapper jsonMapper) {
        this(geminiRestClient, properties, jsonMapper, Duration.ofSeconds(1));
    }

    GeminiClient(RestClient geminiRestClient, GeminiProperties properties, JsonMapper jsonMapper, Duration retryDelay) {
        this.restClient = geminiRestClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.retryDelay = retryDelay;
    }

    public String generateJson(String systemInstruction, String userContent, Map<String, Object> responseSchema) {
        if (!properties.hasApiKey()) {
            log.error("GEMINI_API_KEY is not configured");
            throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "The AI assistant is not available");
        }

        Map<String, Object> generationConfig = Map.of(
                "responseMimeType", "application/json",
                "responseJsonSchema", responseSchema,
                "temperature", 0.3,
                "maxOutputTokens", properties.maxOutputTokens());
        Map<String, Object> request = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userContent)))),
                "generationConfig", generationConfig);

        String requestJson = jsonMapper.writeValueAsString(request);
        String model = properties.model();
        String fallback = properties.fallbackModel();
        String responseBody;
        try {
            responseBody = post(model, requestJson);
        } catch (RestClientException ex) {
            // Overload (503) is retried; a timeout is only retried on a different model, to bound total latency.
            boolean overloaded = ex instanceof RestClientResponseException re && re.getStatusCode().value() == 503;
            boolean timedOut = ex instanceof ResourceAccessException ra && isTimeout(ra);
            if (!overloaded && !(timedOut && !fallback.isEmpty())) {
                throw translate(ex);
            }
            String next = fallback.isEmpty() ? model : fallback;
            log.warn("Gemini model {} unavailable ({}), retrying with {}", model,
                    ex instanceof RestClientResponseException re ? providerError(re) : "timeout", next);
            if (next.equals(model)) {
                pause();
            }
            try {
                responseBody = post(next, requestJson);
            } catch (RestClientException retryEx) {
                throw translate(retryEx);
            }
        }
        return extractText(responseBody);
    }

    private AiServiceException translate(RestClientException ex) {
        if (ex instanceof RestClientResponseException re) {
            return mapHttpError(re);
        }
        if (ex instanceof ResourceAccessException ra) {
            return mapNetworkError(ra);
        }
        return unexpectedResponse(ex);
    }

    private static boolean isTimeout(ResourceAccessException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private String post(String model, String requestJson) {
        return restClient.post()
                .uri("/v1beta/models/{model}:generateContent", model)
                .header("x-goog-api-key", properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .retrieve()
                .body(String.class);
    }

    private void pause() {
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "The AI provider is temporarily unavailable");
        }
    }

    private AiServiceException unexpectedResponse(RestClientException ex) {
        log.warn("Gemini call failed: {}", ex.getClass().getSimpleName());
        return new AiServiceException(HttpStatus.BAD_GATEWAY, "The AI provider returned an unexpected response");
    }

    // Gemini's error status and message are logged for operators only and never returned to clients.
    private String providerError(RestClientResponseException ex) {
        try {
            JsonNode error = jsonMapper.readTree(ex.getResponseBodyAsString()).path("error");
            String message = error.path("message").asString("");
            return "status=" + error.path("status").asString("?") + ", message="
                    + (message.length() > 200 ? message.substring(0, 200) : message);
        } catch (RuntimeException e) {
            return "unparseable error body";
        }
    }

    private AiServiceException mapHttpError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        log.warn("Gemini returned HTTP {} ({})", status, providerError(ex));
        if (status == 429) {
            return new AiServiceException(HttpStatus.TOO_MANY_REQUESTS,
                    "The AI provider rate limit or quota has been reached; try again later");
        }
        if (status == 401 || status == 403 || status == 404) {
            // Bad key, disabled API or unknown model: an operator problem, not a client problem.
            log.error("Gemini rejected the configuration (HTTP {}); check GEMINI_API_KEY and GEMINI_MODEL", status);
            return new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "The AI assistant is not available");
        }
        if (status >= 500) {
            return new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "The AI provider is temporarily unavailable");
        }
        return new AiServiceException(HttpStatus.BAD_GATEWAY, "The AI provider rejected the request");
    }

    private AiServiceException mapNetworkError(ResourceAccessException ex) {
        if (isTimeout(ex)) {
            log.warn("Gemini call timed out");
            return new AiServiceException(HttpStatus.GATEWAY_TIMEOUT, "The AI provider took too long to respond");
        }
        Throwable root = ex.getMostSpecificCause();
        log.warn("Gemini call failed: {}", (root instanceof IOException ? root : ex).getClass().getSimpleName());
        return new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "The AI provider is unreachable");
    }

    private String extractText(String responseBody) {
        try {
            JsonNode root = jsonMapper.readTree(responseBody);
            String blockReason = root.path("promptFeedback").path("blockReason").asString("");
            if (!blockReason.isEmpty()) {
                throw new AiServiceException(HttpStatus.UNPROCESSABLE_ENTITY, "The AI assistant could not process this content");
            }
            JsonNode candidate = root.path("candidates").path(0);
            String finishReason = candidate.path("finishReason").asString("STOP");
            if (CONTENT_BLOCKED.contains(finishReason)) {
                throw new AiServiceException(HttpStatus.UNPROCESSABLE_ENTITY, "The AI assistant could not process this content");
            }
            if (!"STOP".equals(finishReason)) {
                log.warn("Gemini finished with reason {}", finishReason);
                throw malformed();
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false) && part.path("text").isString()) {
                    text.append(part.path("text").stringValue());
                }
            }
            if (text.isEmpty()) {
                throw malformed();
            }
            return text.toString();
        } catch (JacksonException | IllegalArgumentException ex) {
            throw malformed();
        }
    }

    private AiServiceException malformed() {
        return new AiServiceException(HttpStatus.BAD_GATEWAY, "The AI provider returned an unusable response");
    }
}
