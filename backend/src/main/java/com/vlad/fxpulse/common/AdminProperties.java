package com.vlad.fxpulse.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the admin API ({@code /api/admin/**}), bound from {@code app.admin.*}.
 *
 * @param token shared secret that callers send in the {@code X-Admin-Token} header, from the env var
 *              {@code ADMIN_TOKEN}. Empty means the admin API is disabled: a secure default, so forgetting
 *              the variable in the cloud cannot leave the endpoint open.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String token) {

    public boolean isEnabled() {
        return token != null && !token.isBlank();
    }

    /** Never print the secret: a record's default toString() would include every field. */
    @Override
    public String toString() {
        return "AdminProperties[token=" + (isEnabled() ? "***" : "<not set>") + "]";
    }
}
