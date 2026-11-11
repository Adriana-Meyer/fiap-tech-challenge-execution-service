package com.fiap.workshop.execution.infrastructure.observability;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class NewRelicMetricsPublisherTest {

    @Test
    void shouldBeANoOpWhenTheAgentIsNotAttached() {
        assertDoesNotThrow(() -> new NewRelicMetricsPublisher()
                .recordServiceOrderStatusDuration(UUID.randomUUID(), "EXECUTION", 42));
    }
}
