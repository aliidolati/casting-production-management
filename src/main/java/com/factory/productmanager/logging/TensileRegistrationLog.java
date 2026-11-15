package com.factory.productmanager.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class TensileRegistrationLog {
    public static final Logger LOG = LoggerFactory.getLogger("tensile.registration");
    public static final String REQUEST_ID = "tensileRequestId";
    public static final String ACTOR_ID = "tensileActorId";

    private TensileRegistrationLog() {
    }

    public static void identifyActor(UUID actorId) {
        if (MDC.get(REQUEST_ID) != null) {
            MDC.put(ACTOR_ID, actorId.toString());
        }
    }

    public static long elapsedMs(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    public static void afterCommit(UUID pieceId, UUID testId, int testIndex) {
        if (MDC.get(REQUEST_ID) == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                LOG.info("event=test_committed pieceId={} testId={} testIndex={}", pieceId, testId, testIndex);
            }
        });
    }
}
