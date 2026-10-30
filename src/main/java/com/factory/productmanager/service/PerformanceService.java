package com.factory.productmanager.service;

import com.factory.productmanager.exception.*;
import com.factory.productmanager.model.dto.PageResponse;
import com.factory.productmanager.model.dto.PerformanceDtos.*;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class PerformanceService {
    private final UserRepository users;
    private final PerformancePeriodRepository periods;
    private final PerformanceCriterionRepository criteria;
    private final PerformanceEventRepository events;
    private final TaskLookup lookup;
    private final PerformanceAccessPolicy access;
    private final JsonMapper json;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<PeriodSummary> list(UUID userId, String username, int page, int size) {
        visibleUser(userId, username);
        return PageResponse.from(periods.findByUserIdOrderByCreatedAtDescIdDesc(userId, paging(page, size)), this::summary);
    }

    @Transactional(readOnly = true)
    public Period detail(UUID userId, UUID periodId, String username) {
        User actor = visibleUser(userId, username);
        // A shared aggregate lock makes version, totals and rows one consistent observation.
        var p = periods.readOwned(periodId, userId).orElseThrow(this::notFound);
        return response(p, actor);
    }

    @Transactional(readOnly = true)
    public PageResponse<Event> history(UUID userId, UUID periodId, String username, int page, int size) {
        visibleUser(userId, username);
        periods.findByIdAndUserId(periodId, userId).orElseThrow(this::notFound);
        return PageResponse.from(events.findByPeriodIdOrderByOccurredAtDescIdDesc(periodId, paging(page, size)), e ->
                new Event(e.getId(), e.getCriterionId(), e.getAction(), e.getActorId(), e.getActorUsername(),
                        e.getActorName(), e.getOccurredAt(), e.getReason(), e.getOldValues(), e.getNewValues(),
                        e.getOldEfficiency(), e.getNewEfficiency()));
    }

    public Period create(UUID userId, CreatePeriod request, String username) {
        User actor = lockUser(userId, username);
        if (periods.findFirstByUserIdOrderByCreatedAtDesc(userId).isPresent()) throw new DuplicateResourceException("برای شروع دورهٔ بعد از دورهٔ جاری استفاده کنید");
        var p = newPeriod(users.getReferenceById(userId), request.title());
        record(p, actor, "PERIOD_CREATED", null, null, null, Map.of("title", p.getTitle()), null, null);
        return response(p, actor);
    }

    public Period next(UUID userId, UUID periodId, NextPeriod request, String username) {
        User actor = lockUser(userId, username);
        var p = lockPeriod(userId, periodId, request.version());
        if (p.getClosedAt() != null) throw new DuplicateResourceException("این دوره قبلاً بسته شده است");
        var originals = active(p);
        BigDecimal efficiency = PerformanceCalculator.calculate(originals).efficiency();
        p.setClosedAt(now()); p.setOpenUserKey(null);
        touch(p);
        record(p, actor, "PERIOD_CLOSED", null, null, Map.of("status", "OPEN"), Map.of("status", "CLOSED"), efficiency, efficiency);
        // Release the nullable unique open key before inserting the successor.
        periods.flush();
        var successor = newPeriod(p.getUser(), request.title());
        record(successor, actor, "PERIOD_CREATED", null, null, null,
                Map.of("title", successor.getTitle(), "sourcePeriodId", p.getId()), null, null);
        BigDecimal beforeCopy = null;
        BigDecimal fullEfficiency = BigDecimal.ONE.setScale(4);
        for (var original : originals) {
            var copy = new PerformanceCriterion();
            copy.setPeriod(successor); copy.setTitle(original.getTitle()); copy.setDescription(original.getDescription());
            copy.setMaximumScore(original.getMaximumScore()); copy.setWeight(original.getWeight());
            copy.setScore(original.getMaximumScore()); copy.setCreatedAt(now());
            criteria.save(copy);
            // Every copied criterion has its full score; avoid rescanning the growing set per row.
            record(successor, actor, "CRITERION_COPIED", copy.getId(), null, null, criterion(copy), beforeCopy, fullEfficiency);
            beforeCopy = fullEfficiency;
        }
        return response(successor, actor);
    }

    public Period add(UUID userId, UUID periodId, CriterionWrite request, String username) {
        User actor = lockUser(userId, username);
        var p = lockPeriod(userId, periodId, request.version());
        requireReason(p, request.reason(), false);
        if (request.score() != null && request.score().compareTo(request.maximumScore()) != 0)
            throw new IllegalArgumentException("امتیاز اولیه باید برابر سقف امتیاز باشد");
        BigDecimal before = total(p);
        var c = new PerformanceCriterion(); c.setPeriod(p); c.setCreatedAt(now());
        assign(c, request, request.maximumScore());
        criteria.saveAndFlush(c);
        touch(p);
        record(p, actor, "CRITERION_ADDED", c.getId(), request.reason(), null, criterion(c), before, total(p));
        return response(p, actor);
    }

    public Period update(UUID userId, UUID periodId, UUID criterionId, CriterionWrite request, String username) {
        User actor = lockUser(userId, username);
        var p = lockPeriod(userId, periodId, request.version());
        var c = ownedCriterion(p, criterionId);
        if (request.score() == null) throw new IllegalArgumentException("نمرهٔ فعلی را وارد کنید");
        if (request.score().compareTo(request.maximumScore()) > 0) throw new IllegalArgumentException("نمره نمی‌تواند از سقف بیشتر باشد");
        requireReason(p, request.reason(), c.getScore().compareTo(request.score()) != 0);
        var old = criterion(c); BigDecimal before = total(p);
        assign(c, request, request.score());
        criteria.flush(); touch(p);
        record(p, actor, "CRITERION_UPDATED", c.getId(), request.reason(), old, criterion(c), before, total(p));
        return response(p, actor);
    }

    public Period delete(UUID userId, UUID periodId, UUID criterionId, CriterionDelete request, String username) {
        User actor = lockUser(userId, username);
        var p = lockPeriod(userId, periodId, request.version());
        var c = ownedCriterion(p, criterionId);
        requireReason(p, request.reason(), false);
        var old = criterion(c); BigDecimal before = total(p);
        c.setDeletedAt(now()); criteria.flush(); touch(p);
        record(p, actor, "CRITERION_DELETED", c.getId(), request.reason(), old, null, before, total(p));
        return response(p, actor);
    }

    private User visibleUser(UUID id, String username) {
        User actor = lookup.actor(username); access.requireVisible(actor, id);
        if (!users.existsById(id)) throw notFound();
        return actor;
    }
    private User lockUser(UUID id, String username) {
        User actor = lookup.actor(username); access.requireManage(actor, id);
        users.lockForPerformance(id).orElseThrow(this::notFound);
        return actor;
    }
    private PerformancePeriod lockPeriod(UUID userId, UUID periodId, Long version) {
        var p = periods.lockOwned(periodId, userId).orElseThrow(this::notFound);
        if (version == null || p.getVersion() != version) throw new DuplicateResourceException("ارزیابی تغییر کرده است؛ صفحه را تازه کنید");
        return p;
    }
    private PerformanceCriterion ownedCriterion(PerformancePeriod p, UUID id) {
        return criteria.findByIdAndPeriodIdAndDeletedAtIsNull(id, p.getId()).orElseThrow(this::notFound);
    }
    private PerformancePeriod newPeriod(User user, String title) {
        if (periods.findByOpenUserKey(user.getId().toString()).isPresent()) throw new DuplicateResourceException("کاربر دورهٔ باز دارد");
        var p = new PerformancePeriod(); p.setUser(user); p.setTitle(title.trim());
        p.setOpenUserKey(user.getId().toString()); p.setCreatedAt(now()); p.setUpdatedAt(now());
        return periods.saveAndFlush(p);
    }
    private void assign(PerformanceCriterion c, CriterionWrite r, BigDecimal score) {
        c.setTitle(r.title().trim()); c.setDescription(clean(r.description()));
        c.setMaximumScore(r.maximumScore().setScale(2, java.math.RoundingMode.UNNECESSARY));
        c.setWeight(r.weight().setScale(2, java.math.RoundingMode.UNNECESSARY));
        c.setScore(score.setScale(2, java.math.RoundingMode.UNNECESSARY));
    }
    private void requireReason(PerformancePeriod p, String reason, boolean scoreChanged) {
        if ((p.getClosedAt() != null || scoreChanged) && clean(reason) == null)
            throw new IllegalArgumentException("برای تغییر نمره یا اصلاح دورهٔ بسته دلیل را وارد کنید");
    }
    private void touch(PerformancePeriod p) {
        p.setRevision(p.getRevision() + 1); p.setUpdatedAt(now()); periods.flush();
    }
    private List<PerformanceCriterion> active(PerformancePeriod p) {
        return criteria.findByPeriodIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(p.getId());
    }
    private BigDecimal total(PerformancePeriod p) { return PerformanceCalculator.calculate(active(p)).efficiency(); }
    private Criterion criterion(PerformanceCriterion c) {
        return new Criterion(c.getId(), c.getTitle(), c.getDescription(), c.getMaximumScore(), c.getWeight(), c.getScore());
    }
    private PeriodSummary summary(PerformancePeriod p) {
        return new PeriodSummary(p.getId(), p.getTitle(), status(p), p.getVersion(), p.getCreatedAt(), p.getClosedAt());
    }
    private Period response(PerformancePeriod p, User actor) {
        var rows = active(p); var result = PerformanceCalculator.calculate(rows);
        return new Period(p.getId(), p.getTitle(), status(p), p.getVersion(), p.getCreatedAt(), p.getClosedAt(), p.getUpdatedAt(),
                rows.stream().map(this::criterion).toList(), result.weightedScore(), result.weightedMaximum(), result.efficiency(),
                access.canManage(actor), access.canManage(actor) && p.getClosedAt() == null);
    }
    private void record(PerformancePeriod p, User actor, String action, UUID criterionId, String reason,
                        Object oldValues, Object newValues, BigDecimal oldEfficiency, BigDecimal newEfficiency) {
        var e = new PerformanceEvent(); e.setPeriod(p); e.setAction(action); e.setCriterionId(criterionId);
        e.setActorId(actor.getId()); e.setActorUsername(actor.getUsername()); e.setActorName(actor.getFullName());
        e.setOccurredAt(now()); e.setReason(clean(reason));
        e.setOldValues(oldValues == null ? null : json.writeValueAsString(oldValues));
        e.setNewValues(newValues == null ? null : json.writeValueAsString(newValues));
        e.setOldEfficiency(oldEfficiency); e.setNewEfficiency(newEfficiency); events.save(e);
    }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private String status(PerformancePeriod p) { return p.getClosedAt() == null ? "OPEN" : "CLOSED"; }
    private String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private PageRequest paging(int page, int size) { return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size))); }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("ارزیابی پیدا نشد"); }
}
