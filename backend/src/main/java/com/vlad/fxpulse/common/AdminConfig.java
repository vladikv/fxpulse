package com.vlad.fxpulse.common;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Puts {@link AdminTokenInterceptor} in front of every {@code /api/admin/**} endpoint, so a new admin
 * endpoint is protected automatically instead of relying on each controller to check the token.
 *
 * <p>{@code @EnableConfigurationProperties} registers {@link AdminProperties} together with this class,
 * because {@code @WebMvcTest} slices load WebMvcConfigurers but skip {@code @ConfigurationPropertiesScan}
 * (same reason as in {@link CorsConfig}).
 */
@Configuration
@EnableConfigurationProperties(AdminProperties.class)
public class AdminConfig implements WebMvcConfigurer {

    private final AdminProperties properties;

    public AdminConfig(AdminProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminTokenInterceptor(properties)).addPathPatterns("/api/admin/**");
    }
}
