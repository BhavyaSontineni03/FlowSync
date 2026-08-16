package com.expensemanagement.kafka;

import com.expensemanagement.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Provisions one Kafka topic per organization on startup, giving every
 * tenant its own isolated event stream (see EventTopicResolver for why).
 * Runs against whatever orgs already exist in the database; new orgs get
 * their topic lazily created by OrganizationService.registerOrganization
 * instead of waiting for the next restart.
 *
 * Skipped in the "test" profile so unit/integration tests that don't spin up
 * a real broker aren't forced to wait on a connection that will never
 * succeed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test & !loadtest")
public class KafkaTopicInitializer {

    private final KafkaAdmin kafkaAdmin;
    private final OrganizationRepository organizationRepository;
    private final EventTopicResolver topicResolver;
    private final EventsProperties eventsProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void provisionTenantTopics() {
        List<Long> orgIds = organizationRepository.findAll().stream()
                .map(org -> org.getId())
                .toList();

        if (orgIds.isEmpty()) {
            log.info("No organizations found yet; per-tenant topics will be created lazily on first registration.");
            return;
        }

        // Short, explicit timeouts: if the broker isn't reachable (a
        // perfectly normal state for local dev without `docker compose up`,
        // or for a test context that never intended to exercise Kafka),
        // this should fail fast rather than stall application startup for
        // the client's default ~60s timeout.
        Map<String, Object> adminConfig = Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaAdmin.getConfigurationProperties().get(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG),
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000
        );

        try (var admin = org.apache.kafka.clients.admin.Admin.create(adminConfig)) {
            List<NewTopic> topics = orgIds.stream()
                    .map(orgId -> new NewTopic(
                            topicResolver.topicFor(orgId),
                            eventsProperties.getTopicPartitions(),
                            eventsProperties.getTopicReplicationFactor()))
                    .toList();

            var result = admin.createTopics(topics);
            for (NewTopic topic : topics) {
                try {
                    result.values().get(topic.name()).get();
                    log.info("Provisioned tenant topic {}", topic.name());
                } catch (ExecutionException e) {
                    if (e.getCause() != null && e.getCause().getClass().getSimpleName().equals("TopicExistsException")) {
                        log.debug("Tenant topic {} already exists", topic.name());
                    } else {
                        log.warn("Could not provision tenant topic {}: {}", topic.name(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Kafka broker unavailable at startup ({}); per-tenant topics were not provisioned. " +
                    "The saga will retry publishing via the outbox relay once the broker is reachable.", e.getMessage());
        }
    }
}
