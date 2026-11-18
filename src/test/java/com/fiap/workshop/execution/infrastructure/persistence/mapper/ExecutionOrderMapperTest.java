package com.fiap.workshop.execution.infrastructure.persistence.mapper;

import com.fiap.workshop.execution.domain.model.execution.ExecutionOrder;
import com.fiap.workshop.execution.domain.model.execution.ExecutionOrderStatus;
import com.fiap.workshop.execution.domain.model.execution.ExecutionTask;
import com.fiap.workshop.execution.domain.model.execution.TaskStatus;
import com.fiap.workshop.execution.infrastructure.persistence.entity.ExecutionOrderItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExecutionOrderMapperTest {

    private final ExecutionOrderMapper mapper = new ExecutionOrderMapper();

    @Test
    void shouldRoundTripAnOrderWithItsTasksAndVersion() {
        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        ExecutionOrder order = new ExecutionOrder(UUID.randomUUID(), UUID.randomUUID(), List.of(
                new ExecutionTask(UUID.randomUUID(), UUID.randomUUID(), "Oil change", "MECHANICAL",
                        TaskStatus.COMPLETED, "mech1", start, start.plusSeconds(1800)),
                ExecutionTask.pending(UUID.randomUUID(), UUID.randomUUID(), "Alignment", null)),
                ExecutionOrderStatus.IN_PROGRESS, start.minusSeconds(60), null, 3L);

        ExecutionOrderItem item = mapper.toItem(order);
        ExecutionOrder back = mapper.toDomain(item);

        assertEquals(order.getServiceOrderId().toString(), item.getServiceOrderId());
        assertEquals(3L, item.getVersion());
        assertEquals(order.getServiceOrderId(), back.getServiceOrderId());
        assertEquals(ExecutionOrderStatus.IN_PROGRESS, back.getStatus());
        assertEquals(order.getCreatedAt(), back.getCreatedAt());
        assertNull(back.getFinishedAt());
        assertEquals(2, back.getTasks().size());
        ExecutionTask done = back.getTasks().get(0);
        assertEquals("mech1", done.getMechanic());
        assertEquals(TaskStatus.COMPLETED, done.getStatus());
        assertEquals(start, done.getStartedAt());
        assertEquals(30, done.duration().toMinutes());
        assertNull(back.getTasks().get(1).getServiceType());
    }
}
