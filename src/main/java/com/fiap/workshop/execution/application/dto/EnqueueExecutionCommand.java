package com.fiap.workshop.execution.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record EnqueueExecutionCommand(
        @NotNull UUID serviceOrderId,
        @NotEmpty @Valid List<TaskCommand> tasks
) {}
