package com.vlad.fxpulse.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Title and description shown at the top of Swagger UI. The endpoints themselves are discovered
 * by springdoc from the @RestController classes, so there is nothing to keep in sync by hand.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fxPulseOpenApi() {
        return new OpenAPI().info(new Info()
                .title("FxPulse API")
                .version("v1")
                .description("Official NBP (table A) PLN exchange rates: time series, statistics and "
                        + "currency conversion. Dates are ISO yyyy-MM-dd; errors use a common ApiError body."));
    }
}
