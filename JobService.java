package com.jobportal.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.dto.JobRequestDto;
import com.jobportal.dto.JobResponseDto;
import com.jobportal.exception.GlobalExceptionHandler.*;
import com.jobportal.kafka.KafkaEventDto;
import com.jobportal.kafka.KafkaProducerService;
import com.jobportal.model.Job;
import com.jobportal.model.User;
import com.jobportal.repository.ApplicationRepository;
import com.jobportal.repository.JobRepository;
import com.jobportal.repository.UserRepository;
import com.jobportal.security.RedisTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JobService — now with:
 *   Redis:  cache job lists (60s TTL), evict on any write
 *   Kafka:  publish events after status change
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository         jobRepository;
    private final UserRepository        userRepository;
    private final ApplicationRepository applicationRepository;
    private final KafkaProducerService  kafkaProducer;
    private final RedisTokenService     redisService;
    private final ObjectMapper          objectMapper;

    private static final String CACHE_ALL_JOBS  = "jobs:all";
    private static final String CACHE_OPEN_JOBS = "jobs:open";
    private static final long   CACHE_TTL_SEC   = 60L;

    // ── Create job ────────────────────────────────────────────────────────────
    @Transactional
    public JobResponseDto createJob(JobRequestDto dto) {
        User employer = null;
        if (dto.getEmployerId() != null) {
            employer = userRepository.findById(dto.getEmployerId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Employer not found: " + dto.getEmployerId()));
        }

        Job job = Job.builder()
                .title(dto.getTitle())
                .company(dto.getCompany())
                .location(dto.getLocation())
                .description(dto.getDescription())
                .requirements(dto.getRequirements())
                .salary(dto.getSalary())
                .jobType(dto.getJobType())
                .category(dto.getCategory())
                .experienceLevel(dto.getExperienceLevel())
                .deadline(dto.getDeadline())
                .employer(employer)
                .build();

        Job saved = jobRepository.save(job);

        // Evict caches — data changed
        evictJobCaches();

        log.info("Job created: id={} title={}", saved.getId(), saved.getTitle());
        return toDto(saved);
    }

    // ── Get all jobs (with Redis cache) ───────────────────────────────────────
    public List<JobResponseDto> getAllJobs() {
        // Try cache first
        Object cached = redisService.retrieve(CACHE_ALL_JOBS);
        if (cached != null) {
            log.debug("Cache HIT: {}", CACHE_ALL_JOBS);
            return objectMapper.convertValue(cached,
                    new TypeReference<List<JobResponseDto>>() {});
        }

        // Cache miss — hit PostgreSQL
        log.debug("Cache MISS: {} — querying DB", CACHE_ALL_JOBS);
        List<JobResponseDto> jobs = jobRepository.findAll()
                .stream().map(this::toDto).collect(Collectors.toList());

        // Store in Redis for 60 seconds
        redisService.store(CACHE_ALL_JOBS, jobs, CACHE_TTL_SEC);
        return jobs;
    }

    // ── Get open jobs (with Redis cache) ──────────────────────────────────────
    public List<JobResponseDto> getOpenJobs() {
        Object cached = redisService.retrieve(CACHE_OPEN_JOBS);
        if (cached != null) {
            log.debug("Cache HIT: {}", CACHE_OPEN_JOBS);
            return objectMapper.convertValue(cached,
                    new TypeReference<List<JobResponseDto>>() {});
        }

        log.debug("Cache MISS: {} — querying DB", CACHE_OPEN_JOBS);
        List<JobResponseDto> jobs = jobRepository.findByStatus(Job.JobStatus.OPEN)
                .stream().map(this::toDto).collect(Collectors.toList());

        redisService.store(CACHE_OPEN_JOBS, jobs, CACHE_TTL_SEC);
        return jobs;
    }

    // ── Get job by ID ─────────────────────────────────────────────────────────
    public JobResponseDto getJobById(Long id) {
        String cacheKey = "jobs:" + id;
        Object cached = redisService.retrieve(cacheKey);
        if (cached != null) {
            log.debug("Cache HIT: {}", cacheKey);
            return objectMapper.convertValue(cached, JobResponseDto.class);
        }

        JobResponseDto dto = toDto(findJobOrThrow(id));
        redisService.store(cacheKey, dto, CACHE_TTL_SEC);
        return dto;
    }

    // ── Update job ────────────────────────────────────────────────────────────
    @Transactional
    public JobResponseDto updateJob(Long id, JobRequestDto dto) {
        Job job = findJobOrThrow(id);

        job.setTitle(dto.getTitle());
        job.setCompany(dto.getCompany());
        job.setLocation(dto.getLocation());
        job.setDescription(dto.getDescription());
        job.setRequirements(dto.getRequirements());
        job.setSalary(dto.getSalary());
        job.setJobType(dto.getJobType());
        job.setCategory(dto.getCategory());
        job.setExperienceLevel(dto.getExperienceLevel());
        job.setDeadline(dto.getDeadline());

        Job saved = jobRepository.save(job);
        evictJobCaches();
        redisService.evict("jobs:" + id);

        return toDto(saved);
    }

    // ── Delete job ────────────────────────────────────────────────────────────
    @Transactional
    public void deleteJob(Long id) {
        findJobOrThrow(id);
        jobRepository.deleteById(id);
        evictJobCaches();
        redisService.evict("jobs:" + id);
        log.info("Job deleted: id={}", id);
    }

    // ── Update job status + publish Kafka event ───────────────────────────────
    @Transactional
    public JobResponseDto updateJobStatus(Long id, Job.JobStatus newStatus) {
        Job job = findJobOrThrow(id);
        String oldStatus = job.getStatus().name();

        job.setStatus(newStatus);
        Job saved = jobRepository.save(job);

        // Evict cache
        evictJobCaches();
        redisService.evict("jobs:" + id);

        // Publish Kafka event AFTER successful DB save
        KafkaEventDto event = KafkaEventDto.jobStatusChanged(
                saved.getId(), saved.getTitle(),
                oldStatus, newStatus.name()
        );
        kafkaProducer.publishJobStatusChanged(event);

        log.info("Job {} status changed: {} → {}", id, oldStatus, newStatus);
        return toDto(saved);
    }

    // ── Search jobs ───────────────────────────────────────────────────────────
    public List<JobResponseDto> searchJobs(
            String keyword, String location,
            String category, Job.JobType jobType) {
        // Search results are not cached (too many combinations)
        return jobRepository.searchJobs(keyword, location, category, jobType)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ── Get jobs by employer ──────────────────────────────────────────────────
    public List<JobResponseDto> getJobsByEmployer(Long employerId) {
        return jobRepository.findByEmployerId(employerId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ── Salary range filter ───────────────────────────────────────────────────
    public List<JobResponseDto> getJobsBySalaryRange(Double min, Double max) {
        return jobRepository.findBySalaryRange(min, max)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // ── Stats ─────────────────────────────────────────────────────────────────
    public Map<String, Long> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalJobs",         jobRepository.count());
        stats.put("openJobs",          jobRepository.countByStatus(Job.JobStatus.OPEN));
        stats.put("closedJobs",        jobRepository.countByStatus(Job.JobStatus.CLOSED));
        stats.put("totalApplications", applicationRepository.count());
        return stats;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void evictJobCaches() {
        redisService.evict(CACHE_ALL_JOBS);
        redisService.evict(CACHE_OPEN_JOBS);
    }

    private Job findJobOrThrow(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job not found with id: " + id));
    }

    public JobResponseDto toDto(Job job) {
        long appCount = applicationRepository.countByJobId(job.getId());
        return JobResponseDto.builder()
                .id(job.getId())
                .title(job.getTitle())
                .company(job.getCompany())
                .location(job.getLocation())
                .description(job.getDescription())
                .requirements(job.getRequirements())
                .salary(job.getSalary())
                .jobType(job.getJobType())
                .status(job.getStatus())
                .category(job.getCategory())
                .experienceLevel(job.getExperienceLevel())
                .postedAt(job.getPostedAt())
                .deadline(job.getDeadline())
                .employerName(job.getEmployer() != null
                        ? job.getEmployer().getName() : null)
                .applicationCount(appCount)
                .build();
    }
}
