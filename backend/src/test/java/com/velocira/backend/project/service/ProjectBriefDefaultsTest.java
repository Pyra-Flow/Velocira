package com.velocira.backend.project.service;

import com.velocira.backend.project.dto.ProjectBriefRequest;
import com.velocira.backend.project.model.ProjectType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectBriefDefaultsTest {

    @Test
    void derives_a_short_human_title_when_none_is_provided() {
        ProjectBriefRequest request = new ProjectBriefRequest(
                "Build a booking app for a salon where clients choose services and staff.", null, null, null, null);

        assertThat(ProjectBriefDefaults.title(request)).isEqualTo("Booking app for a salon where clients choose services and staff");
    }

    @Test
    void respects_an_optional_title_and_infers_the_project_format() {
        ProjectBriefRequest request = new ProjectBriefRequest(
                "A mobile app for people to plan their running sessions.", "Run better", null, null, null);

        assertThat(ProjectBriefDefaults.title(request)).isEqualTo("Run better");
        assertThat(ProjectBriefDefaults.type(request.brief())).isEqualTo(ProjectType.MOBILE_APP);
    }

    @Test
    void keeps_customization_as_context_instead_of_a_required_setup_field() {
        ProjectBriefRequest request = new ProjectBriefRequest(
                "A feedback dashboard for a small product team.", null, "Product managers", "Weekly summary", "Individual performance scores");

        assertThat(ProjectBriefDefaults.description(request))
                .contains("A feedback dashboard", "Audience: Product managers", "Important to include: Weekly summary", "Avoid: Individual performance scores");
    }
}
