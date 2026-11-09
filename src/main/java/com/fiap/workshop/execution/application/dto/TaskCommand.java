package com.fiap.workshop.execution.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TaskCommand(
        @NotNull UUID taskId,
        @NotNull UUID serviceId,
        @NotBlank String serviceName,
        String serviceType
) {}
