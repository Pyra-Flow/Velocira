package com.velocira.backend.generation.observability;

import com.velocira.backend.generation.model.GenerationJobStatus;
import com.velocira.backend.generation.repository.GenerationJobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/** Exposes durable queue depth rather than an in-memory approximation. */
@Component
public class GenerationQueueDepthGauge {

    public GenerationQueueDepthGauge(GenerationJobRepository generationJobRepository, MeterRegistry meterRegistry) {
        Gauge.builder("velocira.generation.queue.depth", generationJobRepository,
                        repository -> repository.countByStatusIn(EnumSet.of(GenerationJobStatus.QUEUED)))
                .description("Durable generation jobs waiting for a worker")
                .register(meterRegistry);
    }
}
