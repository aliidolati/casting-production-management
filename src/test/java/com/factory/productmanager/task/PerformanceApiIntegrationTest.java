package com.factory.productmanager.task;

import com.factory.productmanager.model.dto.PerformanceDtos.*;
import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.model.entity.Role;
import com.factory.productmanager.repository.*;
import com.factory.productmanager.service.PerformanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PerformanceApiIntegrationTest extends TaskTestBase {
    @Autowired PerformanceService service;
    @Autowired PerformancePeriodRepository periods;
    @Autowired PerformanceEventRepository events;
    @Autowired PerformanceCriterionRepository criteria;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    private String base(UUID id) { return "/api/users/" + id + "/performance/periods"; }
    private String path(Period p) { return base(worker.getId()) + "/" + p.id(); }
    private Period create() { return service.create(worker.getId(), new CreatePeriod("مهر ۱۴۰۵"), admin.getUsername()); }
    private CriterionWrite body(Period p, String title, String maximum, String weight, String score, String reason) {
        return new CriterionWrite(p.version(), title, "شرح معیار", new BigDecimal(maximum), new BigDecimal(weight),
                score == null ? null : new BigDecimal(score), reason);
    }
    private Period add(Period p, String title, String maximum, String weight) {
        return service.add(worker.getId(), p.id(), body(p, title, maximum, weight, null, null), admin.getUsername());
    }

    @Test void acceptedCalculationAndTaskStatisticsRemainIndependent() throws Exception {
        var p = add(create(), "کیفیت", "50", "3");
        var c = p.criteria().get(0);
        p = service.update(worker.getId(), p.id(), c.id(), body(p, "کیفیت", "50", "3", "40", "کاستی کیفیت"), admin.getUsername());
        p = add(p, "نظم", "50", "1");
        call(get(path(p)), worker).andExpect(status().isOk()).andExpect(jsonPath("$.efficiency").value(0.85))
                .andExpect(jsonPath("$.weightedScore").value(170)).andExpect(jsonPath("$.weightedMaximum").value(200))
                .andExpect(jsonPath("$.canManage").value(false)).andExpect(jsonPath("$.canStartNext").value(false));
        call(get("/api/users/" + worker.getId() + "/profile"), worker).andExpect(jsonPath("$.stats.openTasks").value(0))
                .andExpect(jsonPath("$.performance").doesNotExist());
        var history = service.history(worker.getId(), p.id(), worker.getUsername(), 0, 100);
        var change = history.items().stream().filter(e -> e.action().equals("CRITERION_UPDATED")).findFirst().orElseThrow();
        assertEquals("کاستی کیفیت", change.reason()); assertEquals(admin.getId(), change.actorId());
        assertTrue(change.oldValues().contains("50")); assertTrue(change.newValues().contains("40"));
        assertEquals(new BigDecimal("1.0000"), change.oldEfficiency());
        assertEquals(new BigDecimal("0.8000"), change.newEfficiency());
    }

    @Test void authorizationAndNestedOwnershipDoNotLeak() throws Exception {
        var p = add(create(), "کیفیت", "50", "3"); var c = p.criteria().get(0);
        for (var actor : List.of(manager, otherWorker, outsider)) {
            call(get(base(worker.getId())), actor).andExpect(status().isNotFound());
            call(get(path(p)), actor).andExpect(status().isNotFound());
            call(get(path(p) + "/events"), actor).andExpect(status().isNotFound());
            call(patch(path(p) + "/criteria/" + c.id()), actor, body(p, "x", "50", "3", "40", "دلیل")).andExpect(status().isNotFound());
        }
        call(get(base(UUID.randomUUID())), worker).andExpect(status().isNotFound());
        call(post(base(worker.getId())), worker, new CreatePeriod("دوره")).andExpect(status().isForbidden());
        call(post(path(p) + "/next"), worker, new NextPeriod(p.version(), "بعد")).andExpect(status().isForbidden());
        call(post(path(p) + "/criteria"), worker, body(p, "x", "50", "1", null, null)).andExpect(status().isForbidden());
        call(patch(path(p) + "/criteria/" + c.id()), worker, body(p, "x", "50", "3", "40", "دلیل")).andExpect(status().isForbidden());
        call(delete(path(p) + "/criteria/" + c.id()), worker, new CriterionDelete(p.version(), null)).andExpect(status().isForbidden());
        call(get(base(otherWorker.getId()) + "/" + p.id()), admin).andExpect(status().isNotFound());
        var other = service.create(otherWorker.getId(), new CreatePeriod("دوره"), admin.getUsername());
        call(patch(base(otherWorker.getId()) + "/" + other.id() + "/criteria/" + c.id()), admin,
                body(other, "x", "50", "3", "40", "دلیل")).andExpect(status().isNotFound());
        call(get(path(p)), null).andExpect(status().isUnauthorized());
    }

    @Test void invalidInputCannotMutateScores() throws Exception {
        var p = add(create(), "کیفیت", "50", "3"); String url = path(p) + "/criteria/" + p.criteria().get(0).id();
        for (var invalid : List.of(body(p, "x", "0", "3", "0", "دلیل"), body(p, "x", "50", "0", "40", "دلیل"),
                body(p, "x", "50", "3", "-1", "دلیل"), body(p, "x", "50", "3", "1.111", "دلیل"),
                body(p, "x", "50", "3.001", "40", "دلیل"), body(p, " ", "50", "3", "40", "دلیل"),
                body(p, "x", "40", "3", "10000000000", "دلیل"), body(p, "x", "50", "3", "40", " "),
                body(p, "x", "50", "3", null, "دلیل"))) {
            call(patch(url), admin, invalid).andExpect(status().isBadRequest());
        }
        call(post(path(p) + "/criteria"), admin, body(p, "x", "50", "1", "20", null)).andExpect(status().isBadRequest());
        assertEquals(p.version(), service.detail(worker.getId(), p.id(), admin.getUsername()).version());
        assertEquals(new BigDecimal("50.00"), service.detail(worker.getId(), p.id(), admin.getUsername()).criteria().get(0).score());
    }

    @Test void directoryAndScoreGrantsAreIndependentAndCannotManageAccountsOrCriteria() throws Exception {
        var viewer = newUser("directory-" + UUID.randomUUID(), Role.ADMIN, Set.of(Permission.VIEW_USERS));
        var grader = newUser("grader-" + UUID.randomUUID(), Role.ADMIN, Set.of(Permission.EDIT_PERFORMANCE_SCORES));
        var p = add(create(), "کیفیت", "100", "1");
        var criterion = p.criteria().get(0);
        String criterionUrl = path(p) + "/criteria/" + criterion.id();

        call(get("/api/users/directory"), viewer).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + worker.getId() + "' && @.username == '" + worker.getUsername() + "')]").isNotEmpty())
                .andExpect(jsonPath("$..permissions").isEmpty());
        call(get("/api/users"), viewer).andExpect(status().isForbidden());
        call(get("/api/users/directory"), grader).andExpect(status().isForbidden());
        call(patch("/api/users/" + worker.getId() + "/permissions"), viewer, Map.of("permissions", List.of()))
                .andExpect(status().isForbidden());
        call(get(path(p)), viewer).andExpect(status().isNotFound());
        call(get("/api/users/" + worker.getId() + "/profile"), grader).andExpect(status().isNotFound());
        call(get("/api/users/" + worker.getId() + "/performance"), grader).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(worker.getUsername()));
        call(get(path(p)), grader).andExpect(status().isOk()).andExpect(jsonPath("$.canEditScores").value(true))
                .andExpect(jsonPath("$.canManage").value(false)).andExpect(jsonPath("$.canStartNext").value(false));
        call(patch(criterionUrl), grader, body(p, "تغییر ساختار", "120", "2", "110", "تشویقی"))
                .andExpect(status().isForbidden());
        call(post(path(p) + "/criteria"), grader, body(p, "معیار جدید", "100", "1", null, null))
                .andExpect(status().isForbidden());
        call(patch(criterionUrl + "/score"), grader, new ScoreWrite(p.version(), new BigDecimal("110"), null))
                .andExpect(status().isBadRequest());
        call(patch(criterionUrl + "/score"), grader, Map.of("version", p.version(), "score", 110,
                        "reason", "امتیاز تشویقی", "maximumScore", 200))
                .andExpect(status().isOk()).andExpect(jsonPath("$.criteria[0].maximumScore").value(100))
                .andExpect(jsonPath("$.criteria[0].score").value(110)).andExpect(jsonPath("$.efficiency").value(1.1));
        call(patch(criterionUrl + "/score"), grader, new ScoreWrite(p.version(), new BigDecimal("120"), "نسخه قدیمی"))
                .andExpect(status().isConflict());
        var event = service.history(worker.getId(), p.id(), grader.getUsername(), 0, 10).items().stream()
                .filter(e -> "CRITERION_UPDATED".equals(e.action())).findFirst().orElseThrow();
        assertEquals(grader.getId(), event.actorId());
        assertEquals("امتیاز تشویقی", event.reason());
        assertEquals(new BigDecimal("1.1000"), event.newEfficiency());
    }

    @Test void initialAndEditedBonusScoresPreserveTheWeightedRatioAndAudit() {
        var p = create();
        p = service.add(worker.getId(), p.id(), body(p, "کیفیت", "100", "1", "110", "تشویق اولیه"), admin.getUsername());
        assertEquals(new BigDecimal("1.1000"), p.efficiency());
        var criterion = p.criteria().get(0);
        p = service.update(worker.getId(), p.id(), criterion.id(),
                body(p, criterion.title(), "0.01", "1", "9999999999.99", "آزمون ظرفیت عددی"), admin.getUsername());
        var expected = new BigDecimal("999999999999.0000");
        assertEquals(expected, p.efficiency());
        assertTrue(service.history(worker.getId(), p.id(), admin.getUsername(), 0, 10).items().stream()
                .anyMatch(e -> expected.equals(e.newEfficiency())));
    }

    @Test void rolloverCopiesOnlyActiveCriteriaAtFullScoreAndClosedCorrectionsAreIndependent() throws Exception {
        var p = add(add(create(), "کیفیت", "50", "3"), "حذف‌شونده", "20", "1");
        var first = p.criteria().get(0); var removed = p.criteria().get(1);
        p = service.update(worker.getId(), p.id(), first.id(), body(p, "کیفیت", "50", "3", "40", "عملکرد"), admin.getUsername());
        p = service.delete(worker.getId(), p.id(), removed.id(), new CriterionDelete(p.version(), null), admin.getUsername());
        var next = service.next(worker.getId(), p.id(), new NextPeriod(p.version(), "آبان ۱۴۰۵"), admin.getUsername());
        assertEquals(1, next.criteria().size()); assertEquals(new BigDecimal("50.00"), next.criteria().get(0).score());
        assertNotEquals(first.id(), next.criteria().get(0).id()); assertEquals(new BigDecimal("1.0000"), next.efficiency());
        var closed = service.detail(worker.getId(), p.id(), worker.getUsername());
        assertEquals("CLOSED", closed.status()); assertNotNull(closed.closedAt());
        call(patch(path(closed) + "/criteria/" + first.id()), admin, body(closed, "کیفیت", "50", "3", "45", null)).andExpect(status().isBadRequest());
        var corrected = service.update(worker.getId(), p.id(), first.id(), body(closed, "کیفیت", "50", "3", "45", "اصلاح اشتباه"), admin.getUsername());
        assertEquals(new BigDecimal("0.9000"), corrected.efficiency()); assertEquals("CLOSED", corrected.status());
        assertEquals(next, service.detail(worker.getId(), next.id(), admin.getUsername()));
        call(post(path(p) + "/next"), admin, new NextPeriod(p.version(), "تکراری")).andExpect(status().isConflict());
        call(post(base(worker.getId())), admin, new CreatePeriod("تکراری")).andExpect(status().isConflict());
    }

    @Test void closedStructuralChangesRequireReasonAndEmptyScoreIsAbsent() throws Exception {
        var p = create();
        call(get(path(p)), worker).andExpect(jsonPath("$.efficiency").doesNotExist()).andExpect(jsonPath("$.weightedMaximum").value(0));
        service.next(worker.getId(), p.id(), new NextPeriod(p.version(), "بعد"), admin.getUsername());
        var closed = service.detail(worker.getId(), p.id(), admin.getUsername());
        call(post(path(p) + "/criteria"), admin, body(closed, "جدید", "3", "1", null, null)).andExpect(status().isBadRequest());
        closed = service.add(worker.getId(), p.id(), body(closed, "جدید", "3", "1", null, "معیار جا افتاده"), admin.getUsername());
        var c = closed.criteria().get(0);
        call(delete(path(p) + "/criteria/" + c.id()), admin, new CriterionDelete(closed.version(), null)).andExpect(status().isBadRequest());
        closed = service.delete(worker.getId(), p.id(), c.id(), new CriterionDelete(closed.version(), "معیار اشتباه"), admin.getUsername());
        assertNull(closed.efficiency()); assertEquals(1, criteria.findAll().stream().filter(row -> row.getPeriod().getId().equals(p.id()) && row.getDeletedAt() != null).count());
    }

    @Test void staleWritesFailAndAccountDeletionPreservesHistory() throws Exception {
        var p = create(); var changed = add(p, "کیفیت", "50", "3");
        call(post(path(p) + "/criteria"), admin, body(p, "قدیمی", "50", "1", null, null)).andExpect(status().isConflict());
        var criterionId = changed.criteria().get(0).id();
        call(patch(path(p) + "/criteria/" + criterionId), admin, body(p, "قدیمی", "50", "1", "40", "دلیل")).andExpect(status().isConflict());
        call(delete(path(p) + "/criteria/" + criterionId), admin, new CriterionDelete(p.version(), null)).andExpect(status().isConflict());
        call(post(path(p) + "/next"), admin, new NextPeriod(p.version(), "قدیمی")).andExpect(status().isConflict());
        call(delete("/api/users/" + worker.getId()), admin).andExpect(status().isConflict());
        call(patch("/api/users/" + worker.getId() + "/profile"), admin, Map.of("active", false)).andExpect(status().isOk());
        assertEquals(changed, service.detail(worker.getId(), changed.id(), admin.getUsername()));
        call(get(base(worker.getId())).param("size", "1000"), admin).andExpect(jsonPath("$.size").value(100));
    }

    @Test void structuralEditsRecalculateWithoutChangingTheScoreAndPeriodsArePaginated() {
        var p = add(create(), "عنوان اولیه", "50", "3");
        var c = p.criteria().get(0);
        p = service.update(worker.getId(), p.id(), c.id(), body(p, "عنوان جدید", "100", "2", "50", null), admin.getUsername());
        assertEquals("عنوان جدید", p.criteria().get(0).title());
        assertEquals(new BigDecimal("50.00"), p.criteria().get(0).score());
        assertEquals(new BigDecimal("0.5000"), p.efficiency());
        assertEquals(new BigDecimal("100.0000"), p.weightedScore());
        assertEquals(new BigDecimal("200.0000"), p.weightedMaximum());
        var next = service.next(worker.getId(), p.id(), new NextPeriod(p.version(), "دورهٔ بعد"), admin.getUsername());
        var latest = service.list(worker.getId(), admin.getUsername(), 0, 1);
        var older = service.list(worker.getId(), worker.getUsername(), 1, 1);
        assertEquals(2, latest.totalElements()); assertEquals(2, latest.totalPages());
        assertEquals(next.id(), latest.items().get(0).id()); assertEquals(p.id(), older.items().get(0).id());
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCreationAndEditsAndRolloverHaveOnlyOneWinner() throws Exception {
        var otherAdmin = newUser("perf_admin_" + UUID.randomUUID(), Role.MANAGER, Set.of());
        var outcomes = race(() -> service.create(worker.getId(), new CreatePeriod("الف"), admin.getUsername()),
                () -> service.create(worker.getId(), new CreatePeriod("ب"), otherAdmin.getUsername()));
        assertEquals(1, outcomes.stream().filter(Period.class::isInstance).count());
        var p = (Period) outcomes.stream().filter(Period.class::isInstance).findFirst().orElseThrow();
        var full = add(p, "کیفیت", "50", "3"); var id = full.criteria().get(0).id();
        outcomes = race(() -> service.update(worker.getId(), full.id(), id, body(full, "کیفیت", "50", "3", "40", "ادمین اول"), admin.getUsername()),
                () -> service.update(worker.getId(), full.id(), id, body(full, "کیفیت", "50", "3", "45", "ادمین دوم"), otherAdmin.getUsername()));
        assertEquals(1, outcomes.stream().filter(Period.class::isInstance).count());
        var edited = service.detail(worker.getId(), full.id(), admin.getUsername());
        outcomes = race(() -> service.next(worker.getId(), edited.id(), new NextPeriod(edited.version(), "بعد الف"), admin.getUsername()),
                () -> service.next(worker.getId(), edited.id(), new NextPeriod(edited.version(), "بعد ب"), otherAdmin.getUsername()));
        assertEquals(1, outcomes.stream().filter(Period.class::isInstance).count());
        assertEquals(2, periods.findByUserIdOrderByCreatedAtDescIdDesc(worker.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, service.history(worker.getId(), full.id(), admin.getUsername(), 0, 100).items().stream().filter(e -> e.action().equals("CRITERION_UPDATED")).count());
    }

    private List<Object> race(Callable<?> a, Callable<?> b) throws Exception {
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(pool.submit(() -> outcome(start, a)), pool.submit(() -> outcome(start, b)));
            start.countDown(); return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    protected void assertCurrentCriterionValuesAfterAnOlderSnapshot() throws Exception {
        var p = add(create(), "کیفیت", "50", "3");
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.executeWithoutResult(status -> {
            // This read deliberately establishes a snapshot before the writer commits.
            userRepository.findByUsername(admin.getUsername()).orElseThrow();
            var pool = Executors.newSingleThreadExecutor();
            try {
                pool.submit(() -> service.update(worker.getId(), p.id(), p.criteria().get(0).id(),
                        body(p, "کیفیت", "50", "3", "40", "تغییر هم‌زمان"), admin.getUsername())).get(15, TimeUnit.SECONDS);
                var current = service.detail(worker.getId(), p.id(), admin.getUsername());
                assertEquals(p.version() + 1, current.version());
                assertEquals(new BigDecimal("40.00"), current.criteria().get(0).score());
                assertEquals(new BigDecimal("0.8000"), current.efficiency());
            } catch (Exception ex) { throw new AssertionError(ex); }
            finally { pool.shutdownNow(); }
        });
    }
    private Object outcome(CountDownLatch start, Callable<?> operation) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try { return operation.call(); } catch (com.factory.productmanager.exception.DuplicateResourceException ex) { return "CONFLICT"; }
    }
}
