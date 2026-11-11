package com.fiap.workshop.execution.domain.service;

import java.util.UUID;

public interface MetricsPublisher {
    void recordServiceOrderStatusDuration(UUID serviceOrderId, String status, long durationMinutes);
}
