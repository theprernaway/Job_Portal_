package com.jobportal.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * KafkaConsumerService listens to all 3 topics.
 *
 * In a real production app, these consumers would:
 *   - Send transactional emails via SendGrid / AWS SES
 *   - Write audit logs to a separate audit DB
 *   - Push real-time notifications via WebSockets
 *   - Update analytics dashboards
 *
 * Here we show the pattern clearly with log statements.
 * Replace the log.info() calls with your real actions.
 *
 * Each @KafkaListener method:
 *   - Runs in a separate thread from the HTTP request thread
 *   - Retries automatically if it throws an exception
 *   - Processes messages in order within a partition
 */
@Service
@Slf4j
public class KafkaConsumerService {

    // ── Consumer 1: Job Applied ───────────────────────────────────────────────
    @KafkaListener(
        topics     = "${kafka.topic.job-applied}",
        groupId    = "job-portal-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onJobApplied(KafkaEventDto event) {
        log.info("EVENT RECEIVED [{}] — Job: '{}' | Applicant: {} <{}>",
                event.getEventType(),
                event.getJobTitle(),
                event.getApplicantName(),
                event.getApplicantEmail());

        // ── Action 1: Notify the employer ─────────────────────────────────────
        sendEmailToEmployer(
            event.getEmployerEmail(),
            "New application received for: " + event.getJobTitle(),
            event.getApplicantName() + " has applied to your job posting."
        );

        // ── Action 2: Confirm to the applicant ────────────────────────────────
        sendEmailToApplicant(
            event.getApplicantEmail(),
            "Application submitted: " + event.getJobTitle(),
            "Your application to " + event.getCompany() + " has been received."
        );

        // ── Action 3: Write audit log ─────────────────────────────────────────
        writeAuditLog(event);
    }

    // ── Consumer 2: Job Status Changed ───────────────────────────────────────
    @KafkaListener(
        topics     = "${kafka.topic.job-status-changed}",
        groupId    = "job-portal-group"
    )
    public void onJobStatusChanged(KafkaEventDto event) {
        log.info("EVENT RECEIVED [{}] — Job: '{}' changed from {} to {}",
                event.getEventType(),
                event.getJobTitle(),
                event.getOldStatus(),
                event.getNewStatus());

        // If job was closed, notify all applicants (in real app, query their emails)
        if ("CLOSED".equals(event.getNewStatus())) {
            log.info("Job '{}' closed — would notify all pending applicants",
                    event.getJobTitle());
        }

        writeAuditLog(event);
    }

    // ── Consumer 3: Application Status Changed ────────────────────────────────
    @KafkaListener(
        topics     = "${kafka.topic.application-status-changed}",
        groupId    = "job-portal-group"
    )
    public void onApplicationStatusChanged(KafkaEventDto event) {
        log.info("EVENT RECEIVED [{}] — Applicant: {} | Job: '{}' | {} → {}",
                event.getEventType(),
                event.getApplicantName(),
                event.getJobTitle(),
                event.getOldStatus(),
                event.getNewStatus());

        // Craft a message based on the new status
        String subject = buildSubject(event);
        String body    = buildBody(event);

        sendEmailToApplicant(event.getApplicantEmail(), subject, body);
        writeAuditLog(event);
    }

    // ── Email helpers (replace with real email service) ───────────────────────

    private void sendEmailToEmployer(String to, String subject, String body) {
        // Replace with: emailService.send(to, subject, body)
        log.info("EMAIL → EMPLOYER [{}] Subject: {}", to, subject);
    }

    private void sendEmailToApplicant(String to, String subject, String body) {
        // Replace with: emailService.send(to, subject, body)
        log.info("EMAIL → APPLICANT [{}] Subject: {}", to, subject);
    }

    private void writeAuditLog(KafkaEventDto event) {
        // Replace with: auditLogRepository.save(new AuditLog(event))
        log.info("AUDIT LOG: eventType={} jobId={} at {}",
                event.getEventType(), event.getJobId(), event.getOccurredAt());
    }

    // ── Status-based email content ────────────────────────────────────────────

    private String buildSubject(KafkaEventDto event) {
        return switch (event.getNewStatus()) {
            case "REVIEWING"   -> "Your application is under review — " + event.getJobTitle();
            case "SHORTLISTED" -> "Great news! You've been shortlisted — " + event.getJobTitle();
            case "REJECTED"    -> "Application update — " + event.getJobTitle();
            case "HIRED"       -> "Congratulations! You got the job — " + event.getJobTitle();
            default            -> "Application status update — " + event.getJobTitle();
        };
    }

    private String buildBody(KafkaEventDto event) {
        return switch (event.getNewStatus()) {
            case "REVIEWING"   -> "The employer is reviewing your application. Hang tight!";
            case "SHORTLISTED" -> "You've been shortlisted. Expect a call soon.";
            case "REJECTED"    -> "Unfortunately your application was not selected this time.";
            case "HIRED"       -> "You have been selected! The employer will contact you shortly.";
            default            -> "Your application status changed to: " + event.getNewStatus();
        };
    }
}
