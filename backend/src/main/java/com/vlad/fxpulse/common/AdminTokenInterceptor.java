package com.vlad.fxpulse.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Guards {@code /api/admin/**} with a shared secret in the {@code X-Admin-Token} header.
 *
 * <p>Runs before the controller method. Throwing here is fine: Spring passes exceptions from interceptors
 * to the {@code @RestControllerAdvice}, so the client gets the usual {@link ApiError} JSON.
 *
 * <p>This is deliberately simple (one token, no users): enough for one person triggering ingestion and
 * for the daily GitHub Actions job. Real user accounts would use Spring Security.
 */
public class AdminTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Admin-Token";

    private final AdminProperties properties;

    public AdminTokenInterceptor(AdminProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // A CORS preflight (OPTIONS) never carries custom headers. Let the CORS config answer it,
        // otherwise the browser would see a misleading 401 instead of "method not allowed by CORS".
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        if (!properties.isEnabled()) {
            throw new ForbiddenException("Admin API is disabled (ADMIN_TOKEN is not set)");
        }
        String sent = request.getHeader(HEADER);
        if (sent == null || !constantTimeEquals(sent, properties.token())) {
            throw new UnauthorizedException("Missing or invalid " + HEADER + " header");
        }
        return true;
    }

    /**
     * A plain String.equals stops at the first wrong character, and that timing difference could let an
     * attacker guess the token one character at a time. MessageDigest.isEqual compares every byte, but it
     * returns early when the lengths differ, so both values are hashed first: two SHA-256 digests always
     * have 32 bytes, and nothing about the token (not even its length) shows in the timing.
     */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(sha256(a), sha256(b));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime must provide SHA-256, so this can't happen.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
