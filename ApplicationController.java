package com.jobportal.controller;

import com.jobportal.dto.ApplicationRequestDto;
import com.jobportal.dto.ApplicationResponseDto;
import com.jobportal.dto.StatusUpdateDto;
import com.jobportal.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ApplicationController — RBAC rules:
 *
 *   POST   /api/applications              → JOB_SEEKER only (apply)
 *   GET    /api/applications              → ADMIN only (all applications)
 *   GET    /api/applications/{id}         → any authenticated user
 *   GET    /api/applications/applicant/{id} → JOB_SEEKER (own) or ADMIN
 *   GET    /api/applications/job/{jobId}  → EMPLOYER or ADMIN
 *   PATCH  /api/applications/{id}/status  → EMPLOYER only
 *   DELETE /api/applications/{id}         → JOB_SEEKER (withdraw own) or ADMIN
 */
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    // POST /api/applications — JOB_SEEKER applies; triggers Kafka event
    @PostMapping
    @PreAuthorize("hasRole('JOB_SEEKER')")
    public ResponseEntity<ApplicationResponseDto> apply(
            @Valid @RequestBody ApplicationRequestDto dto) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(applicationService.apply(dto));
    }

    // GET /api/applications — ADMIN sees everything
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ApplicationResponseDto>> getAll() {
        return ResponseEntity.ok(applicationService.getAllApplications());
    }

    // GET /api/applications/{id} — any logged-in user
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApplicationResponseDto> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(applicationService.getApplicationById(id));
    }

    // GET /api/applications/applicant/{id} — seeker views own, admin views any
    @GetMapping("/applicant/{applicantId}")
    @PreAuthorize("hasAnyRole('JOB_SEEKER', 'ADMIN')")
    public ResponseEntity<List<ApplicationResponseDto>> getByApplicant(
            @PathVariable Long applicantId) {
        return ResponseEntity.ok(applicationService.getByApplicant(applicantId));
    }

    // GET /api/applications/job/{jobId} — employer reviews candidates for their job
    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasAnyRole('EMPLOYER', 'ADMIN')")
    public ResponseEntity<List<ApplicationResponseDto>> getByJob(
            @PathVariable Long jobId) {
        return ResponseEntity.ok(applicationService.getByJob(jobId));
    }

    // PATCH /api/applications/{id}/status — EMPLOYER shortlists/rejects/hires
    // This triggers a Kafka event → consumer sends email to applicant
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<ApplicationResponseDto> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateDto dto) {
        return ResponseEntity.ok(applicationService.updateStatus(id, dto));
    }

    // DELETE /api/applications/{id} — seeker withdraws, admin can also remove
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('JOB_SEEKER', 'ADMIN')")
    public ResponseEntity<Map<String, String>> withdraw(@PathVariable Long id) {
        applicationService.withdrawApplication(id);
        return ResponseEntity.ok(Map.of("message", "Application withdrawn"));
    }
}
