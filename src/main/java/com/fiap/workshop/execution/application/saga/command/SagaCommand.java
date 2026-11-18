package com.fiap.workshop.execution.application.saga.command;

import java.util.UUID;

/** A request from the OS service's saga orchestrator. */
public interface SagaCommand {

    UUID sagaId();

    UUID serviceOrderId();
}
