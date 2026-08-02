package com.velocira.backend.interview.service;

import com.velocira.backend.interview.dto.InterviewDtos;
import com.velocira.backend.interview.model.InterviewAnswerEntity;
import com.velocira.backend.interview.model.InterviewSessionEntity;
import com.velocira.backend.interview.model.OpenQuestionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Chooses a catalog question through the isolated planner, with a deterministic safe fallback. */
@Service
@RequiredArgsConstructor
public class DiscoveryQuestionPlanner {

    private final DiscoveryQuestionCatalog questionCatalog;
    private final HttpDiscoveryPlannerClient plannerClient;

    public InterviewDtos.QuestionResponse nextQuestion(
            InterviewSessionEntity session,
            List<InterviewAnswerEntity> answers,
            List<OpenQuestionEntity> openQuestions) {
        // Required, evidence-backed answers are enough to start generation.
        // Optional questions remain available through the answer history, but
        // should not make a user complete a long generic questionnaire.
        if (session.getReadinessSnapshot().path("generationReady").asBoolean(false)) {
            return null;
        }
        Optional<DiscoveryQuestionCatalog.QuestionDefinition> remote = plannerClient
                .selectQuestionKey(session.getProject(), answers, openQuestions)
                .flatMap(questionCatalog::findByKey)
                .filter(question -> answers.stream().noneMatch(answer -> answer.getQuestionKey().equals(question.key())));
        return remote.or(() -> questionCatalog.ordered().stream()
                        .filter(question -> answers.stream().noneMatch(answer -> answer.getQuestionKey().equals(question.key())))
                        .findFirst())
                .map(question -> questionCatalog.tailorForProject(question, session.getProject()).toResponse())
                .orElse(null);
    }
}
