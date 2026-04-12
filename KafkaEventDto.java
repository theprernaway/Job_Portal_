package com.jobportal.kafka;

import lombok.*;
import java.time.LocalDateTime;

/**
 * KafkaEventDto is the standard payload sent to ALL Kafka topics.
 *
 * We use one generic shape for all events.
 * The 'eventType' field tells the consumer what happened.
 *
 * Examples:
 *   eventType = "JOB_APPLIED"
 *   eventType = "JOB_STATUS_CHANGED"
 *   eventType = "APPLICATION_STATUS_CHANGED"
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KafkaEventDto {

    private String eventType;        // what happened

    // Job info
    private Long   jobId;
    private String jobTitle;
    private String company;

    // Applicant info
    private Long   applicantId;
    private String applicantName;
    private String applicantEmail;

    // Employer info
    private Long   employerId;
    private String employerEmail;

    // Status change info
    private String oldStatus;
    private String newStatus;

    // When it happened
    private LocalDateTime occurredAt;

    // ── Static factory helpers ────────────────────────────────────────────────

    public static KafkaEventDto jobApplied(
            Long jobId, String jobTitle, String company,
            Long applicantId, String applicantName, String applicantEmail,
            Long employerId, String employerEmail) {
        return KafkaEventDto.builder()
                .eventType("JOB_APPLIED")
                .jobId(jobId)
                .jobTitle(jobTitle)
                .company(company)
                .applicantId(applicantId)
                .applicantName(applicantName)
                .applicantEmail(applicantEmail)
                .employerId(employerId)
                .employerEmail(employerEmail)
                .occurredAt(LocalDateTime.now())
                .build();
    }

    public static KafkaEventDto jobStatusChanged(
            Long jobId, String jobTitle,
            String oldStatus, String newStatus) {
        return KafkaEventDto.builder()
                .eventType("JOB_STATUS_CHANGED")
                .jobId(jobId)
                .jobTitle(jobTitle)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .occurredAt(LocalDateTime.now())
                .build();
    }

    public static KafkaEventDto applicationStatusChanged(
            Long jobId, String jobTitle,
            Long applicantId, String applicantName, String applicantEmail,
            String oldStatus, String newStatus) {
        return KafkaEventDto.builder()
                .eventType("APPLICATION_STATUS_CHANGED")
                .jobId(jobId)
                .jobTitle(jobTitle)
                .applicantId(applicantId)
                .applicantName(applicantName)
                .applicantEmail(applicantEmail)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
