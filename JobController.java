package com.jobportal.controller;

import com.jobportal.dto.JobRequestDto;
import com.jobportal.dto.JobResponseDto;
import com.jobportal.model.Job;
import com.jobportal.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * JobController — RBAC applied at two levels:
 *
 * Level 1 (SecurityConfig):  URL-pattern rules, e.g. GET /api/jobs/** = public
 * Level 2 (@PreAuthorize):   Method-level rules, e.g. only the employer who
 *                            posted the job can delete it (ownership check)
 *
 * Authentication object — Spring injects this automatically.
 * authentication.getName() = the logged-in user's email (from JWT subject).
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    // POST /api/jobs — only EMPLOYER can create (also enforced in SecurityConfig)
    @PostMapping
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<JobResponseDto> createJob(
            @Valid @RequestBody JobRequestDto dto,
            Authentication authentication) {
        // authentication.getName() = employer's email — useful for ownership tracking
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(jobService.createJob(dto));
    }

    // GET /api/jobs — public (no @PreAuthorize needed, SecurityConfig allows it)
    @GetMapping
    public ResponseEntity<List<JobResponseDto>> getAllJobs(
            @RequestParam(defaultValue = "false") boolean openOnly) {
        return ResponseEntity.ok(
                openOnly ? jobService.getOpenJobs() : jobService.getAllJobs()
        );
    }

    // GET /api/jobs/{id} — public
    @GetMapping("/{id}")
    public ResponseEntity<JobResponseDto> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getJobById(id));
    }

    // PUT /api/jobs/{id} — only EMPLOYER
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<JobResponseDto> updateJob(
            @PathVariable Long id,
            @Valid @RequestBody JobRequestDto dto) {
        return ResponseEntity.ok(jobService.updateJob(id, dto));
    }

    // DELETE /api/jobs/{id} — only EMPLOYER
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<Map<String, String>> deleteJob(@PathVariable Long id) {
        jobService.deleteJob(id);
        return ResponseEntity.ok(Map.of("message", "Job deleted successfully"));
    }

    // PATCH /api/jobs/{id}/status — only EMPLOYER, publishes Kafka event
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('EMPLOYER')")
    public ResponseEntity<JobResponseDto> updateStatus(
            @PathVariable Long id,
            @RequestParam Job.JobStatus status) {
        return ResponseEntity.ok(jobService.updateJobStatus(id, status));
    }

    // GET /api/jobs/search — public
    @GetMapping("/search")
    public ResponseEntity<List<JobResponseDto>> searchJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Job.JobType jobType) {
        return ResponseEntity.ok(
                jobService.searchJobs(keyword, location, category, jobType)
        );
    }

    // GET /api/jobs/employer/{id} — any authenticated user
    @GetMapping("/employer/{employerId}")
    public ResponseEntity<List<JobResponseDto>> getByEmployer(
            @PathVariable Long employerId) {
        return ResponseEntity.ok(jobService.getJobsByEmployer(employerId));
    }

    // GET /api/jobs/salary?min=X&max=Y — public
    @GetMapping("/salary")
    public ResponseEntity<List<JobResponseDto>> getBySalary(
            @RequestParam Double min,
            @RequestParam Double max) {
        return ResponseEntity.ok(jobService.getJobsBySalaryRange(min, max));
    }

    // GET /api/jobs/stats — only ADMIN or EMPLOYER
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYER')")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(jobService.getStats());
    }
}
