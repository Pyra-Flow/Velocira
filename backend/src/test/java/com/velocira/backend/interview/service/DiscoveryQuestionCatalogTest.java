package com.velocira.backend.interview.service;

import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.model.ProjectType;
import com.velocira.backend.interview.model.InterviewCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryQuestionCatalogTest {

    private final DiscoveryQuestionCatalog catalog = new DiscoveryQuestionCatalog();

    @Test
    void keepsEveryBaselineQuestionShortAndFocused() {
        assertThat(catalog.ordered()).hasSize(13);
        assertThat(catalog.ordered()).allSatisfy(question -> {
            assertThat(question.questionText()).endsWith("?");
            assertThat(question.questionText().chars().filter(character -> character == '?').count()).isEqualTo(1);
            assertThat(question.questionText().split("\\s+")).hasSizeLessThanOrEqualTo(14);
            assertThat(question.whyWeAsk()).isNotBlank();
        });
    }

    @Test
    void recognizesLegalIntakeBeforeGenericAppointmentBooking() {
        ProjectEntity project = ProjectEntity.builder()
                .name("Mobile-first client intake portal")
                .description("A portal for an independent legal practice with secure uploads, eligibility questions, and appointment requests.")
                .type(ProjectType.MOBILE_APP)
                .build();

        DiscoveryQuestionCatalog.QuestionDefinition problem = catalog.tailorForProject(
                catalog.requireByKey("problem"), project);

        assertThat(problem.questionText()).contains("client intake").doesNotContain("booking");
        assertThat(problem.options()).extracting(DiscoveryQuestionCatalog.ChoiceOption::label)
                .contains("Reduce intake drop-off", "Shorten review time", "Prevent missing client context");
    }

    @Test
    void keepsEveryLegalIntakeQuestionReadableAndAnswerable() {
        ProjectEntity project = ProjectEntity.builder()
                .name("Client intake portal")
                .description("A legal services product for prospective clients to submit intake details and documents.")
                .type(ProjectType.WEB_APP)
                .build();

        assertThat(catalog.ordered()).allSatisfy(question -> {
            DiscoveryQuestionCatalog.QuestionDefinition tailored = catalog.tailorForProject(question, project);
            assertThat(tailored.questionText()).endsWith("?");
            assertThat(tailored.questionText().split("\\s+")).hasSizeLessThanOrEqualTo(18);
            assertThat(tailored.options()).isNotEmpty();
        });
    }

    @Test
    void serviceBookingApplicabilityAddsCrossCuttingCoverageWithoutFixtureTerms() {
        ProjectEntity project = ProjectEntity.builder()
                .name("LocalPro")
                .description("Customers discover and book local service professionals by category, location, and availability.")
                .type(ProjectType.WEB_APP)
                .build();

        assertThat(catalog.serviceBookingApplies(project, List.of())).isTrue();
        assertThat(catalog.requiredCategories(project, List.of()))
                .containsExactlyInAnyOrder(InterviewCategory.values());

        DiscoveryQuestionCatalog.QuestionDefinition users = catalog.tailorForProject(catalog.requireByKey("users"), project);
        DiscoveryQuestionCatalog.QuestionDefinition entities = catalog.tailorForProject(catalog.requireByKey("entities"), project);
        DiscoveryQuestionCatalog.QuestionDefinition risks = catalog.tailorForProject(catalog.requireByKey("risks"), project);
        assertThat(users.questionText()).isEqualTo("Which named role has authority to confirm a booking?");
        assertThat(entities.questionText()).isEqualTo("Which booking record is authoritative?")
                .doesNotContain("view", "access end", "retain", "delete");
        assertThat(risks.questionText()).isEqualTo("Which booking failure would create the most customer or operational harm?")
                .doesNotContain("detect", "communicate", "resolve");
        assertThat(catalog.ordered().stream()
                .flatMap(question -> catalog.tailorForProject(question, project).options().stream())
                .map(option -> option.label() + " " + option.description()))
                .noneMatch(value -> value.toLowerCase().contains("cleaner"));
    }
}
