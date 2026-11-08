package com.vlad.fxpulse.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * CORS settings, bound from {@code app.cors.*} in application.yml.
 *
 * @param allowedOrigins browser origins (scheme + host + port) that may call {@code /api/**}, e.g.
 *                       {@code http://localhost:5173}. From the env var {@code CORS_ALLOWED_ORIGINS} as a
 *                       comma-separated list; Spring Boot splits it into the list.
 */
@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@NotEmpty List<@NotBlank String> allowedOrigins) {
}
