package com.expensemanagement.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the per-organization Kafka topic for domain events.
 *
 * Each org gets its own topic -- "expense-events.org-3" rather than a single
 * shared topic partitioned by org id. That is a deliberate isolation
 * decision, not just a naming convention: a shared topic means one noisy or
 * backlogged tenant's consumer lag delays every other tenant sharing that
 * topic's partitions, and topic-level ACLs/retention/quotas can only be set
 * for the whole topic. Per-org topics let each tenant's event stream be
 * throttled, retained, and access-controlled independently, which matters
 * once different orgs are paying customers on different plans.
 *
 * The tradeoff -- more topics for the cluster to track -- is fine at the
 * scale this system targets (tens to low hundreds of orgs); a shared,
 * key-partitioned topic would be the better call well beyond that.
 */
@Component
@RequiredArgsConstructor
public class EventTopicResolver {

    private final EventsProperties properties;

    public String topicFor(Long orgId) {
        return properties.getTopicPrefix() + ".org-" + orgId;
    }
}
