package com.vlad.fxpulse.common;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the React frontend, served from another origin (Vite dev server on :5173, a static site in the
 * cloud), call the API from the browser. Without it the browser blocks the responses (same-origin policy).
 *
 * <p>Only GET is allowed: the UI only reads. The admin endpoint {@code POST /api/admin/ingest} stays
 * usable from curl/Postman (CORS is enforced by browsers only), but a web page on an allowed origin
 * cannot trigger it. No credentials (cookies) are allowed, because the API does not use them.
 *
 * <p>{@code @EnableConfigurationProperties} registers {@link CorsProperties} together with this class:
 * {@code @WebMvcTest} slices load WebMvcConfigurers but skip {@code @ConfigurationPropertiesScan}, so
 * without it every controller test would fail with "no bean of type CorsProperties".
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties properties;

    public CorsConfig(CorsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET")
                .maxAge(3600);   // browsers may cache the preflight answer for an hour
    }
}
