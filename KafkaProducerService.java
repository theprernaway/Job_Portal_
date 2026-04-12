package com.jobportal.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * KafkaProducerService publishes events to Kafka topics.
 *
 * We call this from ApplicationService and JobService
 * AFTER the database transaction succeeds.
 *
 * The send is async — we don't block the HTTP response waiting for Kafka.
 * We just log success/failure in the callback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.job-applied}")
    private String jobAppliedTopic;

    @Value("${kafka.topic.job-status-changed}")
    private String jobStatusChangedTopic;

    @Value("${kafka.topic.application-status-changed}")
    private String applicationStatusChangedTopic;

    // ── Publish: someone applied to a job ─────────────────────────────────────
    public void publishJobApplied(KafkaEventDto event) {
        publish(jobAppliedTopic, event.getJobId().toString(), event);
    }

    // ── Publish: job opened or closed ─────────────────────────────────────────
    public void publishJobStatusChanged(KafkaEventDto event) {
        publish(jobStatusChangedTopic, event.getJobId().toString(), event);
    }

    // ── Publish: application shortlisted/rejected/hired ───────────────────────
    public void publishApplicationStatusChanged(KafkaEventDto event) {
        publish(applicationStatusChangedTopic, event.getApplicantId().toString(), event);
    }

    // ── Internal send helper ──────────────────────────────────────────────────
    /**
     * Sends message to Kafka asynchronously.
     *
     * Key = entity ID — ensures all events for the same job/applicant
     * go to the same partition (ordering guarantee per entity).
     */
    private void publish(String topic, String key, Object payload) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, key, payload);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish to topic={} key={}: {}",
                        topic, key, ex.getMessage());
            } else {
                log.info("Published to topic={} partition={} offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
