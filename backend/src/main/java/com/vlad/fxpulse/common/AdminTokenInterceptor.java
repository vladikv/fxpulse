package com.vlad.fxpulse.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
     * MessageDigest.isEqual compares every byte, however early the first difference is. A plain
     * String.equals stops at the first wrong character, and that timing difference could let an
     * attacker guess the token one character at a time.
     */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
