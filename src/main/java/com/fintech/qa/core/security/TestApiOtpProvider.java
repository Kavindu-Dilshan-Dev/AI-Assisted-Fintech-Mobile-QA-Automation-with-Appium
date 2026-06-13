package com.fintech.qa.core.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.qa.core.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link OtpProvider} that fetches a one-time passcode from a test mailbox/backend over HTTP.
 *
 * <p>The endpoint is read from non-secret config key {@code otp.api.url}; the bearer token is read
 * ONLY from the environment via {@link ConfigManager#get(String)} resolving {@code otp.api.token}
 * to env var {@code OTP_API_TOKEN}. No secret is ever read from {@code config.properties}.</p>
 *
 * <p>The response is parsed leniently: if it is JSON, the first of {@code otp}, {@code code},
 * {@code otpCode}, or {@code value} is used; otherwise the first 4-8 digit run in the body is taken.
 * Selected when {@code otp.provider=testapi}.</p>
 */
public final class TestApiOtpProvider implements OtpProvider {

    private static final Logger log = LoggerFactory.getLogger(TestApiOtpProvider.class);

    /** Config key for the (non-secret) test backend URL. */
    private static final String OTP_API_URL_KEY = "otp.api.url";

    /** Config key whose env mapping ({@code OTP_API_TOKEN}) holds the bearer token. */
    private static final String OTP_API_TOKEN_KEY = "otp.api.token";

    /** Config key for the HTTP request timeout in seconds. */
    private static final String OTP_API_TIMEOUT_KEY = "otp.api.timeout.seconds";
    private static final int DEFAULT_TIMEOUT_SECONDS = 15;

    /** JSON field names searched, in order, for the OTP value. */
    private static final String[] JSON_KEYS = {"otp", "code", "otpCode", "value"};

    /** Fallback: first 4-8 digit run in a plain-text body. */
    private static final Pattern DIGIT_RUN = Pattern.compile("\\b(\\d{4,8})\\b");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;

    /** Creates a provider with a default {@link HttpClient}. */
    public TestApiOtpProvider() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /**
     * Creates a provider with a caller-supplied {@link HttpClient} (useful for testing).
     *
     * @param httpClient the HTTP client to use; must not be {@code null}
     */
    public TestApiOtpProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Fetches the OTP for the supplied user from the configured test backend.
     *
     * @param userId the mailbox/user key appended as the {@code userId} query parameter
     * @return the fetched one-time passcode
     * @throws IllegalStateException if {@code otp.api.url} is not configured
     * @throws OtpFetchException      if the request fails, returns a non-2xx status, or yields no OTP
     */
    @Override
    public String fetchOtp(String userId) {
        String baseUrl = ConfigManager.get(OTP_API_URL_KEY, "");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "otp.api.url is not configured; cannot use TestApiOtpProvider");
        }

        URI uri = buildUri(baseUrl, userId);
        // Secret comes ONLY from env via ConfigManager (otp.api.token -> OTP_API_TOKEN).
        String token = ConfigManager.get(OTP_API_TOKEN_KEY);
        int timeoutSeconds = ConfigManager.getInt(OTP_API_TIMEOUT_KEY, DEFAULT_TIMEOUT_SECONDS);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Accept", "application/json")
                .GET();
        if (token != null && !token.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        log.info("Fetching OTP for user from test backend: {}",
                MaskingUtil.mask(uri.toString()));

        try {
            HttpResponse<String> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new OtpFetchException(
                        "Test OTP backend returned HTTP " + status + " for user request");
            }
            String otp = parseOtp(response.body());
            if (otp == null || otp.isBlank()) {
                throw new OtpFetchException(
                        "Test OTP backend response did not contain a recognizable OTP");
            }
            log.debug("Fetched OTP from test backend: {}", MaskingUtil.mask("otp: " + otp));
            return otp;
        } catch (OtpFetchException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw new OtpFetchException("I/O error contacting test OTP backend", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OtpFetchException("Interrupted while contacting test OTP backend", e);
        }
    }

    /** Builds the request URI, appending the user id as a query parameter when supplied. */
    private static URI buildUri(String baseUrl, String userId) {
        if (userId == null || userId.isBlank()) {
            return URI.create(baseUrl);
        }
        String encoded = URLEncoder.encode(userId, StandardCharsets.UTF_8);
        String separator = baseUrl.contains("?") ? "&" : "?";
        return URI.create(baseUrl + separator + "userId=" + encoded);
    }

    /** Parses the OTP from a JSON or plain-text body. */
    private static String parseOtp(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode root = MAPPER.readTree(trimmed);
                String fromJson = extractFromJson(root);
                if (fromJson != null) {
                    return fromJson;
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.debug("OTP response was not valid JSON, falling back to digit scan");
            }
        }
        Matcher matcher = DIGIT_RUN.matcher(trimmed);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Searches a JSON node (and its first array element) for a known OTP field. */
    private static String extractFromJson(JsonNode root) {
        JsonNode node = (root.isArray() && !root.isEmpty()) ? root.get(0) : root;
        for (String key : JSON_KEYS) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    /** Unchecked exception signalling that an OTP could not be retrieved from the test backend. */
    public static final class OtpFetchException extends RuntimeException {
        OtpFetchException(String message) {
            super(message);
        }

        OtpFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
