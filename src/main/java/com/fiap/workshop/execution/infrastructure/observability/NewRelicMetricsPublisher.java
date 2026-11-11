package com.fiap.workshop.execution.infrastructure.observability;

import com.fiap.workshop.execution.domain.service.MetricsPublisher;
import com.newrelic.api.agent.NewRelic;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

// Feeds the "tempo médio de execução por status" dashboard (NRQL:
// SELECT average(durationMinutes) FROM ServiceOrderStatusDuration FACET status).
// A no-op when the New Relic agent isn't attached (e.g. in tests/local dev
// without -javaagent) — that's the newrelic-api library's own behavior,
// not something handled here.
@Service
public class NewRelicMetricsPublisher implements MetricsPublisher {

    @Override
    public void recordServiceOrderStatusDuration(UUID serviceOrderId, String status, long durationMinutes) {
        NewRelic.getAgent().getInsights().recordCustomEvent("ServiceOrderStatusDuration", Map.of(
                "serviceOrderId", serviceOrderId.toString(),
                "status", status,
                "durationMinutes", durationMinutes
        ));
    }
}
