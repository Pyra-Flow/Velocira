package com.velocira.backend.generation.event;

import com.velocira.backend.generation.service.GenerationJobWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Dispatches only after the job acceptance transaction has committed. */
@Component
@RequiredArgsConstructor
public class GenerationJobDispatchListener {

    private final GenerationJobWorker generationJobWorker;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(GenerationJobEvent event) {
        if ("QUEUED".equals(event.status())) {
            generationJobWorker.process(event.jobId());
        }
    }

    /** The retry scheduler fires only after the retry state transaction committed. */
    @EventListener
    public void dispatchRetry(GenerationJobRetryEvent event) {
        generationJobWorker.process(event.jobId());
    }
}
