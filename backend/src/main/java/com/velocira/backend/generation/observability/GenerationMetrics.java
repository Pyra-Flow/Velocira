package com.velocira.backend.generation.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** Minimal, provider-neutral generation metrics exported through Actuator. */
@Component
public class GenerationMetrics {

    private final MeterRegistry registry;

    public GenerationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void requested() {
        Counter.builder("velocira.generation.jobs.requested").register(registry).increment();
    }

    public void completed(long latencyMs, double costCents) {
        Counter.builder("velocira.generation.jobs.completed").register(registry).increment();
        Counter.builder("velocira.generation.cost.cents").register(registry).increment(costCents);
        Timer.builder("velocira.generation.latency").register(registry).record(latencyMs, TimeUnit.MILLISECONDS);
    }

    public void failed(String code) {
        Counter.builder("velocira.generation.jobs.failed")
                .tag("code", code == null ? "UNKNOWN" : code)
                .register(registry)
                .increment();
    }

    public void cancelled() {
        Counter.builder("velocira.generation.jobs.cancelled").register(registry).increment();
    }
}
