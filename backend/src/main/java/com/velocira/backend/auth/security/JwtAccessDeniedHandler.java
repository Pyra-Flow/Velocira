package com.velocira.backend.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocira.backend.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom access denied handler that returns a JSON error response
 * when an authenticated user lacks the required permissions.
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /**
     * Handles access denied by returning a JSON 403 response.
     *
     * @param request               the HTTP request
     * @param response              the HTTP response
     * @param accessDeniedException the access denied exception
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        log.warn("Access denied on [{}] for user: {}",
                request.getRequestURI(), accessDeniedException.getMessage());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Void> body = ApiResponse.error(
                "You do not have permission to access this resource.",
                HttpStatus.FORBIDDEN.value());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
