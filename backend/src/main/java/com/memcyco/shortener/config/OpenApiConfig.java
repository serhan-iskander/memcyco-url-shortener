package com.memcyco.shortener.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI memcycoOpenApi() {
        return new OpenAPI().info(new Info()
                .title("memcyco URL shortener API")
                .version("0.1.0")
                .description("URL shortener with pluggable code-generation strategies, "
                        + "Redis-cached redirects, async batched click tracking, and "
                        + "JSONB-backed analytics.")
                .contact(new Contact().name("Serhan Iskander").email("ser_ask@yahoo.com"))
                .license(new License().name("MIT")));
    }
}
