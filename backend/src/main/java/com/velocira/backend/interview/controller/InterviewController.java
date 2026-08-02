package com.velocira.backend.interview.controller;

import com.velocira.backend.auth.security.AuthenticatedUser;
import com.velocira.backend.common.dto.ApiResponse;
import com.velocira.backend.interview.dto.InterviewDtos;
import com.velocira.backend.interview.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Owner-scoped API for adaptive requirements discovery and brief confirmation. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/projects/{projectId}/interview")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Discovery interview", description = "Evidence-backed project requirements discovery")
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping("/start")
    @Operation(summary = "Start or resume discovery", description = "Creates one durable owner-scoped interview session for a project.")
    public ResponseEntity<ApiResponse<InterviewDtos.SessionResponse>> start(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(
                interviewService.start(projectId, principal.getUserId()), "Discovery interview is ready"));
    }

    @GetMapping
    @Operation(summary = "Get discovery summary", description = "Returns current answers, visible gaps, assumptions, decisions, and canonical brief.")
    public ResponseEntity<ApiResponse<InterviewDtos.SessionResponse>> summary(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(
                interviewService.summary(projectId, principal.getUserId()), "Discovery summary retrieved"));
    }

    @GetMapping("/history")
    @Operation(summary = "Get answer evidence history", description = "Returns prior answer revisions without overwriting evidence.")
    public ResponseEntity<ApiResponse<InterviewDtos.HistoryResponse>> history(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(
                interviewService.history(projectId, principal.getUserId()), "Interview history retrieved"));
    }

    @PostMapping("/answers")
    @Operation(summary = "Record an interview answer", description = "Accepts an answer, explicit unknown, or skip and recalculates readiness transparently.")
    public ResponseEntity<ApiResponse<InterviewDtos.SessionResponse>> answer(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody InterviewDtos.AnswerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                interviewService.submitAnswer(projectId, principal.getUserId(), request), "Interview answer recorded"));
    }

    @PutMapping("/answers/{answerId}")
    @Operation(summary = "Revise an answer", description = "Preserves evidence history and recalculates any affected gaps and assumptions.")
    public ResponseEntity<ApiResponse<InterviewDtos.SessionResponse>> reviseAnswer(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID projectId,
            @PathVariable UUID answerId,
            @Valid @RequestBody InterviewDtos.AnswerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                interviewService.reviseAnswer(projectId, answerId, principal.getUserId(), request),
                "Interview answer revised"));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm project brief", description = "Confirms the current brief. Generation becomes available only when deterministic blockers are absent.")
    public ResponseEntity<ApiResponse<InterviewDtos.SessionResponse>> confirm(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(
                interviewService.confirm(projectId, principal.getUserId()), "Project brief confirmed"));
    }

    @PostMapping("/reopen")
    @Operation(summary = "Reopen project brief", description = "Returns the project to discovery before answers are changed.")
    public ResponseEntity<ApiResponse<InterviewDtos.SessionResponse>> reopen(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(
                interviewService.reopen(projectId, principal.getUserId()), "Project brief reopened"));
    }
}
