package com.fiap.workshop.execution.application.dto;

import java.util.UUID;

public record AverageExecutionTimeResponse(
        UUID serviceId,
        String serviceName,
        String serviceType,
        double averageMinutes,
        double averageHours,
        long sampleCount
) {}
