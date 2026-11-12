package com.fiap.workshop.execution.application.saga.event;

import java.util.UUID;

/** An answer to the OS service. The messageId lets the orchestrator ignore a repeated delivery. */
public interface SagaEvent {

    UUID messageId();

    UUID sagaId();

    UUID serviceOrderId();
}
