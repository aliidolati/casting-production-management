package com.factory.productmanager.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.UUID;

import static com.factory.productmanager.logging.TensileRegistrationLog.ACTOR_ID;
import static com.factory.productmanager.logging.TensileRegistrationLog.REQUEST_ID;
import static org.junit.jupiter.api.Assertions.*;

class TensileRegistrationLoggingTest {
    private final Logger logger = (Logger) TensileRegistrationLog.LOG;
    private final ListAppender<ILoggingEvent> entries = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };
    private final UUID pieceId = UUID.randomUUID();
    private final TensileRegistrationLogFilter filter = new TensileRegistrationLogFilter();

    @BeforeEach
    void captureLogEvents() {
        entries.start();
        logger.addAppender(entries);
    }

    @AfterEach
    void stopCapture() {
        logger.detachAppender(entries);
        entries.stop();
    }

    @Test
    void correlatesRequestsAndDoesNotLeakAuthenticatedActorAcrossRequests() throws Exception {
        UUID actor = UUID.randomUUID();
        var first = new MockHttpServletResponse();
        var second = new MockHttpServletResponse();

        filter.doFilter(registration("POST"), first, (request, response) -> {
            TensileRegistrationLog.identifyActor(actor);
            first.setStatus(201);
        });
        filter.doFilter(registration("POST"), second, (request, response) -> second.setStatus(401));
        filter.doFilter(registration("GET"), new MockHttpServletResponse(), (request, response) -> {});

        assertEquals(4, entries.list.size());
        String firstId = first.getHeader("X-Tensile-Request-Id");
        String secondId = second.getHeader("X-Tensile-Request-Id");
        assertNotNull(firstId);
        assertNotNull(secondId);
        assertNotEquals(firstId, secondId);
        assertEquals(firstId, entries.list.get(0).getMDCPropertyMap().get(REQUEST_ID));
        assertTrue(entries.list.get(0).getFormattedMessage().contains("event=request_received pieceId=" + pieceId));
        assertEquals(firstId, entries.list.get(1).getMDCPropertyMap().get(REQUEST_ID));
        assertEquals(actor.toString(), entries.list.get(1).getMDCPropertyMap().get(ACTOR_ID));
        assertTrue(entries.list.get(1).getFormattedMessage().contains("httpStatus=201"));
        assertEquals(secondId, entries.list.get(3).getMDCPropertyMap().get(REQUEST_ID));
        assertFalse(entries.list.get(3).getMDCPropertyMap().containsKey(ACTOR_ID));
        assertTrue(entries.list.get(3).getFormattedMessage().contains("httpStatus=401"));
        assertNull(MDC.get(REQUEST_ID));
        assertNull(MDC.get(ACTOR_ID));
    }

    @Test
    void logsAResponseWriteFailureWithoutLoggingItsExceptionMessage() {
        var response = new MockHttpServletResponse();
        assertThrows(IOException.class, () -> filter.doFilter(registration("POST"), response, (request, reply) -> {
            throw new IOException("synthetic sensitive message");
        }));

        assertEquals(2, entries.list.size());
        var failure = entries.list.get(1);
        assertEquals(response.getHeader("X-Tensile-Request-Id"), failure.getMDCPropertyMap().get(REQUEST_ID));
        assertTrue(failure.getFormattedMessage().contains("event=request_failed"));
        assertTrue(failure.getFormattedMessage().contains("errorType=IOException"));
        assertFalse(failure.getFormattedMessage().contains("synthetic sensitive message"));
        assertNull(MDC.get(REQUEST_ID));
    }

    @Test
    void reportsTheTestIdentifierOnlyAfterCommit() {
        UUID test = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();
        try (var ignored = MDC.putCloseable(REQUEST_ID, "synthetic-request")) {
            TensileRegistrationLog.afterCommit(pieceId, test, 3);
            assertTrue(entries.list.isEmpty());

            var rolledBack = TransactionSynchronizationManager.getSynchronizations().get(0);
            rolledBack.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            assertTrue(entries.list.isEmpty());

            TensileRegistrationLog.afterCommit(pieceId, test, 4);
            var committed = TransactionSynchronizationManager.getSynchronizations().get(1);
            committed.afterCommit();
            assertEquals(1, entries.list.size());
            var event = entries.list.get(0);
            assertEquals("synthetic-request", event.getMDCPropertyMap().get(REQUEST_ID));
            assertTrue(event.getFormattedMessage().contains("event=test_committed pieceId=" + pieceId));
            assertTrue(event.getFormattedMessage().contains("testId=" + test + " testIndex=4"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private MockHttpServletRequest registration(String method) {
        return new MockHttpServletRequest(method, "/api/pieces/" + pieceId + "/tensile-tests");
    }
}
