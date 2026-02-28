package com.velocira.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger UI configuration.
 *
 * <p>
 * Configures the Swagger documentation accessible at
 * {@code /swagger-ui.html}. Includes API metadata, contact info,
 * license, and server URLs.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:Velocira Backend}")
    private String appName;

    /**
     * Configures the OpenAPI specification.
     *
     * @return the OpenAPI configuration
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(appName + " API")
                        .version("1.0.0")
                        .description("""
                                RESTful API for the Velocira platform.

                                ## Authentication
                                Most endpoints require a valid JWT access token.
                                Obtain one via `POST /v1/auth/login` or `POST /v1/auth/google`.
                                Include it in the `Authorization: Bearer <token>` header.
                                """)
                        .contact(new Contact()
                                .name("Velocira Team")
                                .email("support@velocira.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080/api")
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api.velocira.com/api")
                                .description("Production Server")));
    }
}
