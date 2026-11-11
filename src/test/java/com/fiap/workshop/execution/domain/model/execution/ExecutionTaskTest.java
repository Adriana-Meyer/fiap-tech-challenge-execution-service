package com.fiap.workshop.execution.domain.model.execution;

import com.fiap.workshop.execution.domain.exception.InvalidStateException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionTaskTest {

    private ExecutionTask pending() {
        return ExecutionTask.pending(UUID.randomUUID(), UUID.randomUUID(), "Oil change", "MECHANICAL");
    }

    @Test
    void shouldStartPending() {
        ExecutionTask task = pending();
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertNull(task.getMechanic());

        task.start("mech1");

        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
        assertEquals("mech1", task.getMechanic());
        assertNotNull(task.getStartedAt());
    }

    @Test
    void shouldRequireAMechanicToStart() {
        assertThrows(IllegalArgumentException.class, () -> pending().start(" "));
        assertThrows(IllegalArgumentException.class, () -> pending().start(null));
    }

    @Test
    void shouldNotStartTwice() {
        ExecutionTask task = pending();
        task.start("mech1");

        assertThrows(InvalidStateException.class, () -> task.start("mech2"));
        assertEquals("mech1", task.getMechanic());
    }

    @Test
    void shouldFinishOnlyWhenInProgress() {
        ExecutionTask task = pending();
        assertThrows(InvalidStateException.class, task::finish);

        task.start("mech1");
        task.finish();

        assertTrue(task.isCompleted());
        assertNotNull(task.getFinishedAt());
        assertThrows(InvalidStateException.class, task::finish);
    }

    @Test
    void shouldComputeDurationOnlyWhenCompleted() {
        ExecutionTask open = pending();
        assertFalse(open.isCompleted());
        assertThrows(InvalidStateException.class, open::duration);

        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        ExecutionTask done = new ExecutionTask(UUID.randomUUID(), UUID.randomUUID(), "x", "GENERAL",
                TaskStatus.COMPLETED, "m", start, start.plusSeconds(90 * 60));
        assertEquals(90, done.duration().toMinutes());
    }

    @Test
    void shouldValidateTheFactoryArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionTask.pending(null, UUID.randomUUID(), "x", "GENERAL"));
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionTask.pending(UUID.randomUUID(), null, "x", "GENERAL"));
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionTask.pending(UUID.randomUUID(), UUID.randomUUID(), " ", "GENERAL"));
    }
}
