package com.fiap.workshop.execution.application.saga.event;

import java.util.UUID;

/** The order could not be queued (for instance, it has no tasks). Nothing was stored. */
public record ExecutionEnqueueFailed(UUID messageId, UUID sagaId, UUID serviceOrderId, String reason)
        implements SagaEvent {}
