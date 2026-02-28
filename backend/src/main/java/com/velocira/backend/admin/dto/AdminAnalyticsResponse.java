package com.velocira.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Platform analytics response for the admin dashboard.
 *
 * @author Velocira Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin analytics/overview response")
public class AdminAnalyticsResponse {

    @Schema(description = "Total registered users", example = "1542")
    private long totalUsers;

    @Schema(description = "Users registered in the last 30 days", example = "87")
    private long newUsersLast30Days;

    @Schema(description = "Total projects created", example = "3421")
    private long totalProjects;

    @Schema(description = "Projects created in the last 30 days", example = "156")
    private long newProjectsLast30Days;

    @Schema(description = "Total documents generated", example = "12305")
    private long totalDocuments;

    @Schema(description = "Completed documents (successfully generated)", example = "11890")
    private long completedDocuments;

    @Schema(description = "Project count by type", example = "{\"WEB_APP\":1200,\"MOBILE_APP\":800}")
    private Map<String, Long> projectsByType;

    @Schema(description = "Active users (logged in within last 7 days)", example = "324")
    private long activeUsersLast7Days;

    @Schema(description = "Total audit events recorded", example = "54321")
    private long totalAuditEvents;
}
