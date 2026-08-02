package com.velocira.backend.generation.controller;

import com.velocira.backend.auth.security.AuthenticatedUser;
import com.velocira.backend.common.dto.ApiResponse;
import com.velocira.backend.generation.dto.CreateGenerationJobRequest;
import com.velocira.backend.generation.dto.GenerationJobResponse;
import com.velocira.backend.generation.service.GenerationJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Owner-scoped API for the durable asynchronous generation lifecycle. */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/v1/projects/{projectId}/generation-jobs")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Generation jobs", description = "Asynchronous project artifact generation")
public class GenerationJobController {

    private final GenerationJobService generationJobService;

    @GetMapping
    @Operation(summary = "List generation jobs", description = "Returns durable project generation jobs, newest first.")
    public ResponseEntity<ApiResponse<Page<GenerationJobResponse>>> listJobs(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        Page<GenerationJobResponse> jobs = generationJobService.listJobs(
                projectId,
                principal.getUserId(),
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(jobs, "Generation jobs retrieved successfully"));
    }

    @PostMapping
    @Operation(summary = "Queue a generation job", description = "Captures an immutable input snapshot and queues one small Phase 2 test artifact.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Job accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Idempotent replay resolved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Idempotency key was reused for a different request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Generation guardrail exceeded")
    })
    public ResponseEntity<ApiResponse<GenerationJobResponse>> createJob(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID projectId,
            @Parameter(description = "Unique client key reused after an uncertain network result", required = true)
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreateGenerationJobRequest request) {
        GenerationJobService.JobAcceptance acceptance = generationJobService.requestJob(
                projectId, principal.getUserId(), request, idempotencyKey);
        HttpStatus status = acceptance.newlyCreated() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        String message = acceptance.newlyCreated()
                ? "Generation job accepted"
                : "Existing generation job returned for this idempotency key";
        return ResponseEntity.status(status)
                .body(ApiResponse.success(acceptance.job(), message, status.value()));
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get generation job", description = "Returns the latest durable job state after refresh or polling.")
    public ResponseEntity<ApiResponse<GenerationJobResponse>> getJob(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID projectId,
            @PathVariable UUID jobId) {
        GenerationJobResponse job = generationJobService.getJob(projectId, jobId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(job, "Generation job retrieved successfully"));
    }

    @PostMapping("/{jobId}/cancel")
    @Operation(summary = "Cancel generation job", description = "Persists cancellation and prevents an incomplete response from being published.")
    public ResponseEntity<ApiResponse<GenerationJobResponse>> cancelJob(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID projectId,
            @PathVariable UUID jobId) {
        GenerationJobResponse job = generationJobService.cancelJob(projectId, jobId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(job, "Generation job cancellation recorded"));
    }

    @PostMapping("/{jobId}/retry")
    @Operation(summary = "Retry a failed generation job", description = "Creates a new durable retry from the original immutable snapshot.")
    public ResponseEntity<ApiResponse<GenerationJobResponse>> retryJob(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey) {
        GenerationJobService.JobAcceptance acceptance = generationJobService.retryJob(
                projectId, jobId, principal.getUserId(), idempotencyKey);
        HttpStatus status = acceptance.newlyCreated() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        String message = acceptance.newlyCreated()
                ? "Generation retry accepted"
                : "Existing retry job returned for this idempotency key";
        return ResponseEntity.status(status)
                .body(ApiResponse.success(acceptance.job(), message, status.value()));
    }
}
