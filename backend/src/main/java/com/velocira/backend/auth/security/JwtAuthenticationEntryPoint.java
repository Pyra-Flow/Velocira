package com.velocira.backend.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocira.backend.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom authentication entry point that returns a JSON error response
 * when an unauthenticated user tries to access a protected resource.
 *
 * <p>
 * Without this, Spring Security would return a default HTML error page
 * or redirect to a login page — neither appropriate for a REST API.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * Handles unauthorized access attempts by returning a JSON 401 response.
     *
     * @param request       the HTTP request
     * @param response      the HTTP response
     * @param authException the authentication exception
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        log.warn("Unauthorized access attempt on [{}]: {}",
                request.getRequestURI(), authException.getMessage());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Void> body = ApiResponse.error(
                "Authentication required. Please provide a valid access token.",
                HttpStatus.UNAUTHORIZED.value());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
