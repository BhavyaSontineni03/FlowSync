package com.expensemanagement.kafka;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds the app.events.* block from application.yml. */
@Component
@ConfigurationProperties(prefix = "app.events")
@Data
public class EventsProperties {
    private String topicPrefix = "expense-events";
    private int topicPartitions = 3;
    private short topicReplicationFactor = 1;
}
