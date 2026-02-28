package com.velocira.backend.admin.dto;

import com.velocira.backend.auth.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for changing a user's role.
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Change user role request")
public class ChangeRoleRequest {

    @NotNull(message = "New role is required")
    @Schema(description = "The new role to assign", example = "ADMIN", requiredMode = Schema.RequiredMode.REQUIRED)
    private Role role;
}
