package com.devtrack.devtrack_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.regex.Pattern;

/** The API key may be blank: the app still starts and the AI endpoint answers 503 until it is configured. */
@ConfigurationProperties(prefix = "app.ai.gemini")
public record GeminiProperties(String apiKey, String model, String fallbackModel, String baseUrl,
                               Duration connectTimeout, Duration readTimeout, int maxOutputTokens) {

    public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final Duration MAX_TIMEOUT = Duration.ofMinutes(2);

    public GeminiProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        fallbackModel = fallbackModel == null ? "" : fallbackModel.trim();
        baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(25) : readTimeout;
        maxOutputTokens = maxOutputTokens == 0 ? 2048 : maxOutputTokens;

        if (!MODEL_NAME.matcher(model).matches()) {
            throw new IllegalStateException("Configuration error: GEMINI_MODEL contains invalid characters");
        }
        if (!fallbackModel.isEmpty() && !MODEL_NAME.matcher(fallbackModel).matches()) {
            throw new IllegalStateException("Configuration error: GEMINI_FALLBACK_MODEL contains invalid characters");
        }
        if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://localhost")) {
            throw new IllegalStateException("Configuration error: app.ai.gemini.base-url must use https");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero() || connectTimeout.compareTo(MAX_TIMEOUT) > 0
                || readTimeout.isNegative() || readTimeout.isZero() || readTimeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalStateException("Configuration error: Gemini timeouts must be between 1ms and 2m");
        }
        if (maxOutputTokens < 256 || maxOutputTokens > 8192) {
            throw new IllegalStateException("Configuration error: app.ai.gemini.max-output-tokens must be 256-8192");
        }
    }

    public boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    @Override
    public String toString() {
        return "GeminiProperties[model=" + model + ", fallbackModel=" + fallbackModel + ", baseUrl=" + baseUrl + ", apiKey=" + (hasApiKey() ? "****" : "<unset>") + "]";
    }
}
