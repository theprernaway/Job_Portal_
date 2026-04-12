package com.jobportal.service;

import com.jobportal.dto.ApplicationRequestDto;
import com.jobportal.dto.ApplicationResponseDto;
import com.jobportal.dto.StatusUpdateDto;
import com.jobportal.exception.GlobalExceptionHandler.*;
import com.jobportal.kafka.KafkaEventDto;
import com.jobportal.kafka.KafkaProducerService;
import com.jobportal.model.Application;
import com.jobportal.model.Job;
import com.jobportal.model.User;
import com.jobportal.repository.ApplicationRepository;
import com.jobportal.repository.JobRepository;
import com.jobportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ApplicationService — now publishes Kafka events:
 *   apply()        → publishes JOB_APPLIED
 *   updateStatus() → publishes APPLICATION_STATUS_CHANGED
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JobRepository         jobRepository;
    private final UserRepository        userRepository;
    private final KafkaProducerService  kafkaProducer;

    // ── Apply to job ──────────────────────────────────────────────────────────
    @Transactional
    public ApplicationResponseDto apply(ApplicationRequestDto dto) {

        // 1. Find the job
        Job job = jobRepository.findById(dto.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job not found: " + dto.getJobId()));

        // 2. Job must be OPEN
        if (job.getStatus() != Job.JobStatus.OPEN) {
            throw new JobClosedException(
                    "Job is not accepting applications. Status: " + job.getStatus());
        }

        // 3. Find applicant
        User applicant = userRepository.findById(dto.getApplicantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + dto.getApplicantId()));

        // 4. Prevent duplicate applications
        if (applicationRepository.existsByJobIdAndApplicantId(
                dto.getJobId(), dto.getApplicantId())) {
            throw new DuplicateApplicationException(
                    "You have already applied to this job.");
        }

        // 5. Save application to PostgreSQL
        Application application = Application.builder()
                .job(job)
                .applicant(applicant)
                .coverLetter(dto.getCoverLetter())
                .resumeUrl(dto.getResumeUrl())
                .build();

        Application saved = applicationRepository.save(application);

        // 6. Publish Kafka event AFTER successful DB save
        // Consumer will send emails to both applicant and employer
        KafkaEventDto event = KafkaEventDto.jobApplied(
                job.getId(),
                job.getTitle(),
                job.getCompany(),
                applicant.getId(),
                applicant.getName(),
                applicant.getEmail(),
                job.getEmployer() != null ? job.getEmployer().getId()   : null,
                job.getEmployer() != null ? job.getEmployer().getEmail() : null
        );
        kafkaProducer.publishJobApplied(event);

        log.info("Application saved: jobId={} applicantId={}",
                job.getId(), applicant.getId());
        return toDto(saved);
    }

    // ── Get all applications ──────────────────────────────────────────────────
    public List<ApplicationResponseDto> getAllApplications() {
        return applicationRepository.findAll()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ── Get by ID ─────────────────────────────────────────────────────────────
    public ApplicationResponseDto getApplicationById(Long id) {
        return toDto(findOrThrow(id));
    }

    // ── Get all applications by a specific applicant ──────────────────────────
    public List<ApplicationResponseDto> getByApplicant(Long applicantId) {
        return applicationRepository.findByApplicantId(applicantId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ── Get all applications for a specific job ───────────────────────────────
    public List<ApplicationResponseDto> getByJob(Long jobId) {
        return applicationRepository.findByJobId(jobId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ── Employer updates status → triggers Kafka event ────────────────────────
    @Transactional
    public ApplicationResponseDto updateStatus(Long id, StatusUpdateDto dto) {
        Application application = findOrThrow(id);
        String oldStatus = application.getStatus().name();

        application.setStatus(dto.getStatus());
        if (dto.getNotes() != null) {
            application.setEmployerNotes(dto.getNotes());
        }

        Application saved = applicationRepository.save(application);

        // Publish Kafka event — consumer sends email to applicant
        KafkaEventDto event = KafkaEventDto.applicationStatusChanged(
                application.getJob().getId(),
                application.getJob().getTitle(),
                application.getApplicant().getId(),
                application.getApplicant().getName(),
                application.getApplicant().getEmail(),
                oldStatus,
                dto.getStatus().name()
        );
        kafkaProducer.publishApplicationStatusChanged(event);

        log.info("Application {} status: {} → {}",
                id, oldStatus, dto.getStatus().name());
        return toDto(saved);
    }

    // ── Withdraw application ──────────────────────────────────────────────────
    @Transactional
    public void withdrawApplication(Long id) {
        findOrThrow(id);
        applicationRepository.deleteById(id);
        log.info("Application withdrawn: id={}", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Application findOrThrow(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application not found with id: " + id));
    }

    private ApplicationResponseDto toDto(Application app) {
        return ApplicationResponseDto.builder()
                .id(app.getId())
                .jobId(app.getJob().getId())
                .jobTitle(app.getJob().getTitle())
                .company(app.getJob().getCompany())
                .applicantId(app.getApplicant().getId())
                .applicantName(app.getApplicant().getName())
                .coverLetter(app.getCoverLetter())
                .resumeUrl(app.getResumeUrl())
                .status(app.getStatus())
                .employerNotes(app.getEmployerNotes())
                .appliedAt(app.getAppliedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
