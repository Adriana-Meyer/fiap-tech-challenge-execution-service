package com.fiap.workshop.execution.application.saga.event;

import java.util.UUID;

public record ExecutionEnqueued(UUID messageId, UUID sagaId, UUID serviceOrderId) implements SagaEvent {}
