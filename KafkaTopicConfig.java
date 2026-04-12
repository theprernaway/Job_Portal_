package com.jobportal.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * KafkaTopicConfig auto-creates topics when the app starts.
 * If the topic already exists, this does nothing (idempotent).
 *
 * Topics we use:
 *   job.applied                 → fired when someone applies to a job
 *   job.status.changed          → fired when job is opened/closed
 *   application.status.changed  → fired when employer shortlists/rejects/hires
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topic.job-applied}")
    private String jobAppliedTopic;

    @Value("${kafka.topic.job-status-changed}")
    private String jobStatusChangedTopic;

    @Value("${kafka.topic.application-status-changed}")
    private String applicationStatusChangedTopic;

    @Bean
    public NewTopic jobAppliedTopic() {
        return TopicBuilder.name(jobAppliedTopic)
                .partitions(3)      // 3 partitions = 3 consumers can process in parallel
                .replicas(1)        // 1 replica (use 3 in production clusters)
                .build();
    }

    @Bean
    public NewTopic jobStatusChangedTopic() {
        return TopicBuilder.name(jobStatusChangedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic applicationStatusChangedTopic() {
        return TopicBuilder.name(applicationStatusChangedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
