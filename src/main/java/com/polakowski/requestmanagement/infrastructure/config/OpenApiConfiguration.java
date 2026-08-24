package com.polakowski.requestmanagement.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Describes the API so that it can be explored without a GUI of its own. */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI requestManagementOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Request Management API")
                .version("v1")
                .description("Manages the lifecycle of requests according to a configurable state diagram."));
    }
}
