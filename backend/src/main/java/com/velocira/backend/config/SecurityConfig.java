package com.velocira.backend.config;

import com.velocira.backend.auth.security.JwtAccessDeniedHandler;
import com.velocira.backend.auth.security.JwtAuthenticationEntryPoint;
import com.velocira.backend.auth.security.JwtAuthenticationFilter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration.
 *
 * <p>
 * Configures stateless JWT authentication with the following rules:
 * </p>
 * <ul>
 * <li>CSRF is disabled (stateless API)</li>
 * <li>Sessions are stateless (no HttpSession)</li>
 * <li>Auth endpoints ({@code /v1/auth/**}) are publicly accessible (except
 * logout endpoints)</li>
 * <li>Swagger/OpenAPI endpoints are publicly accessible</li>
 * <li>Actuator health endpoint is publicly accessible</li>
 * <li>All other endpoints require authentication</li>
 * <li>JWT filter runs before UsernamePasswordAuthenticationFilter</li>
 * <li>Custom entry point for 401 and access denied handler for 403</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT", description = "JWT access token. Obtain via /v1/auth/login or /v1/auth/google")
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    /** Public auth endpoints (no authentication required). */
    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/v1/auth/register",
            "/v1/auth/login",
            "/v1/auth/google",
            "/v1/auth/verify-email",
            "/v1/auth/resend-otp",
            "/v1/auth/forgot-password",
            "/v1/auth/reset-password",
            "/v1/auth/refresh"
    };

    /** Swagger / OpenAPI endpoints. */
    private static final String[] SWAGGER_ENDPOINTS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml"
    };

    /**
     * Configures the {@link SecurityFilterChain}.
     *
     * @param http the HttpSecurity builder
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF (stateless REST API)
                .csrf(AbstractHttpConfigurer::disable)

                // CORS configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Session management — completely stateless
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Exception handling
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public auth endpoints
                        .requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
                        // Swagger
                        .requestMatchers(SWAGGER_ENDPOINTS).permitAll()
                        // Actuator health
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // OPTIONS requests for CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Admin endpoints — require ADMIN role
                        .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                        // Everything else requires authentication
                        .anyRequest().authenticated())

                // Add JWT filter before the default authentication filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Password encoder using BCrypt.
     *
     * @return a BCryptPasswordEncoder instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Exposes the {@link AuthenticationManager} for injection.
     *
     * @param authConfig the authentication configuration
     * @return the AuthenticationManager
     * @throws Exception if retrieval fails
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * CORS configuration allowing requests from the frontend.
     *
     * @return the CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "https://*.velocira.com"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
