package com.factory.productmanager.repository;

import com.factory.productmanager.model.dto.TrackingListFilter;
import com.factory.productmanager.model.entity.HeatTreatmentSubStage;
import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.PieceDefinition;
import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.PieceStageHistory;
import com.factory.productmanager.model.entity.PieceSubStageHistory;
import com.factory.productmanager.model.entity.Project;
import com.factory.productmanager.model.entity.RadiographySubStage;
import com.factory.productmanager.model.entity.RoughMachiningSubStage;
import com.factory.productmanager.model.entity.SubStageKind;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RequiredArgsConstructor
public class PieceRepositoryImpl implements PieceRepositoryCustom {
    private final EntityManager entityManager;

    @Override
    public Page<UUID> findTrackingPieceIds(TrackingListFilter filter, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<UUID> query = cb.createQuery(UUID.class);
        Root<Piece> piece = query.from(Piece.class);
        query.select(piece.get("id"));
        query.where(buildPredicates(cb, query, piece, filter).toArray(Predicate[]::new));
        query.orderBy(buildOrders(cb, query, piece, filter));

        TypedQuery<UUID> typed = entityManager.createQuery(query);
        typed.setFirstResult((int) pageable.getOffset());
        typed.setMaxResults(pageable.getPageSize());
        List<UUID> ids = typed.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Piece> countRoot = countQuery.from(Piece.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(buildPredicates(cb, countQuery, countRoot, filter).toArray(Predicate[]::new));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(ids, pageable, total);
    }

    @Override
    public List<Object[]> countTrackingGroupedByStage(TrackingListFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<Piece> piece = query.from(Piece.class);
        query.multiselect(piece.get("currentStage"), cb.count(piece));
        query.where(buildPredicates(cb, query, piece, filter).toArray(Predicate[]::new));
        query.groupBy(piece.get("currentStage"));
        return entityManager.createQuery(query).getResultList();
    }

    private List<Predicate> buildPredicates(
            CriteriaBuilder cb,
            CriteriaQuery<?> query,
            Root<Piece> piece,
            TrackingListFilter filter
    ) {
        Join<Piece, PieceDefinition> definition = piece.join("pieceDefinition", JoinType.INNER);
        Join<PieceDefinition, Project> project = definition.join("project", JoinType.INNER);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isNull(piece.get("archivedAt")));

        if (filter.isIncludeStageAndSubstage() && filter.getStage() != null) {
            predicates.add(cb.equal(piece.get("currentStage"), filter.getStage()));
        }
        if (filter.isIncludeStageAndSubstage() && filter.getRadiographySubStage() != null) {
            predicates.add(cb.equal(piece.get("radiographySubStage"), filter.getRadiographySubStage()));
        }
        if (filter.isIncludeStageAndSubstage() && filter.getHeatTreatmentSubStage() != null) {
            predicates.add(cb.equal(piece.get("heatTreatmentSubStage"), filter.getHeatTreatmentSubStage()));
        }
        if (filter.isIncludeStageAndSubstage() && filter.getRoughMachiningSubStage() != null) {
            predicates.add(cb.equal(piece.get("roughMachiningSubStage"), filter.getRoughMachiningSubStage()));
        }

        String keyword = filter.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            PieceSearchTerm term = PieceSearchTerm.parse(keyword);
            if (term.numeric()) {
                predicates.add(term.pieceNumber() == null ? cb.disjunction()
                        : cb.equal(piece.get("pieceNumber"), term.pieceNumber()));
            } else {
                String pattern = likeContains(term.keyword());
                predicates.add(cb.or(
                        cb.like(cb.lower(project.get("code")), pattern, '\\'),
                        cb.like(cb.lower(definition.get("pieceName")), pattern, '\\'),
                        cb.like(piece.get("pieceNumber").as(String.class), pattern, '\\')
                ));
            }
        }

        String pieceName = filter.getPieceName();
        if (pieceName != null && !pieceName.isBlank()) {
            predicates.add(cb.like(cb.lower(definition.get("pieceName")), likeContains(pieceName), '\\'));
        }

        String projectCode = filter.getProjectCode();
        if (projectCode != null && !projectCode.isBlank()) {
            predicates.add(cb.like(cb.lower(project.get("code")), likeContains(projectCode), '\\'));
        }

        if (filter.getEnteredFrom() != null || filter.getEnteredToExclusive() != null) {
            Expression<LocalDateTime> enteredAt = stageEnteredAtExpression(cb, query, piece);
            if (filter.getEnteredFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(enteredAt, filter.getEnteredFrom()));
            }
            if (filter.getEnteredToExclusive() != null) {
                predicates.add(cb.lessThan(enteredAt, filter.getEnteredToExclusive()));
            }
        }

        return predicates;
    }

    private List<Order> buildOrders(
            CriteriaBuilder cb,
            CriteriaQuery<?> query,
            Root<Piece> piece,
            TrackingListFilter filter
    ) {
        boolean asc = "asc".equalsIgnoreCase(filter.getSortDir());
        List<Order> orders = new ArrayList<>();
        String sortBy = filter.getSortBy() == null ? "" : filter.getSortBy();

        if ("processDate".equals(sortBy)) {
            orders.add(cb.asc(cb.selectCase()
                    .when(cb.isNull(piece.get("processDate")), 1)
                    .otherwise(0)));
            orders.add(asc ? cb.asc(piece.get("processDate")) : cb.desc(piece.get("processDate")));
        } else if ("stageEnteredAt".equals(sortBy)) {
            Expression<LocalDateTime> enteredAt = stageEnteredAtExpression(cb, query, piece);
            orders.add(asc ? cb.asc(enteredAt) : cb.desc(enteredAt));
        } else if ("pieceNumber".equals(sortBy)) {
            orders.add(asc ? cb.asc(piece.get("pieceNumber")) : cb.desc(piece.get("pieceNumber")));
        } else {
            orders.add(cb.desc(piece.get("createdAt")));
        }
        // ترتیب ثابت برای مقادیر برابر (مثلاً انتقال دسته‌جمعی) تا جدول و صفحه‌بندی بهم نریزد
        if (!"pieceNumber".equals(sortBy)) {
            orders.add(cb.asc(piece.get("pieceNumber")));
        }
        orders.add(cb.desc(piece.get("id")));
        return orders;
    }

    /**
     * «تاریخ ورود به مرحله» دقیقاً با همان منطقی که در PieceService برای نمایش محاسبه می‌شود:
     * اگر قطعه در مرحله‌ای با زیرمرحله باشد (رادیوگرافی، عملیات حرارتی، ماشین‌کاری خشن)
     * آخرین ورود به همان زیرمرحله ملاک است و در نبودِ آن آخرین تغییر مرحله؛
     * در نبودِ هر دو، تاریخ ثبت قطعه.
     * هم‌خوان بودن این عبارت با مقدار نمایش‌داده‌شده برای درست بودن مرتب‌سازی و فیلتر ضروری است.
     */
    private Expression<LocalDateTime> stageEnteredAtExpression(
            CriteriaBuilder cb,
            CriteriaQuery<?> query,
            Root<Piece> piece
    ) {
        Subquery<LocalDateTime> latest = query.subquery(LocalDateTime.class);
        Root<PieceStageHistory> history = latest.from(PieceStageHistory.class);
        latest.select(cb.function("max", LocalDateTime.class, history.get("changedAt")));
        latest.where(cb.equal(history.get("piece"), piece));
        Expression<LocalDateTime> stageAt = cb.coalesce(latest, piece.get("createdAt"));

        Expression<LocalDateTime> radiographyAt = subStageEnteredAt(
                cb, query, piece, SubStageKind.RADIOGRAPHY, "radiographySubStage",
                RadiographySubStage.values(), RadiographySubStage.SHOT);
        Expression<LocalDateTime> heatAt = subStageEnteredAt(
                cb, query, piece, SubStageKind.HEAT_TREATMENT, "heatTreatmentSubStage",
                HeatTreatmentSubStage.values(), HeatTreatmentSubStage.AWAITING_OPERATIONS);
        Expression<LocalDateTime> roughAt = subStageEnteredAt(
                cb, query, piece, SubStageKind.ROUGH_MACHINING, "roughMachiningSubStage",
                RoughMachiningSubStage.values(), RoughMachiningSubStage.DEBURRING);

        return cb.<LocalDateTime>selectCase()
                .when(cb.equal(piece.get("currentStage"), PieceStage.RADIOGRAPHY), cb.coalesce(radiographyAt, stageAt))
                .when(cb.equal(piece.get("currentStage"), PieceStage.HEAT_TREATMENT), cb.coalesce(heatAt, stageAt))
                .when(cb.equal(piece.get("currentStage"), PieceStage.ROUGH_MACHINING), cb.coalesce(roughAt, stageAt))
                .otherwise(stageAt);
    }

    /**
     * آخرین زمان ورود قطعه به زیرمرحله‌ی فعلی‌اش از نوع {@code kind}
     * (در صورت خالی بودن زیرمرحله، مقدار پیش‌فرض همانند StageTransitionPolicy لحاظ می‌شود).
     */
    private <E extends Enum<E>> Expression<LocalDateTime> subStageEnteredAt(
            CriteriaBuilder cb,
            CriteriaQuery<?> query,
            Root<Piece> piece,
            SubStageKind kind,
            String attribute,
            E[] values,
            E defaultValue
    ) {
        CriteriaBuilder.Case<String> currentName = cb.selectCase();
        for (E value : values) {
            if (value != defaultValue) {
                currentName = currentName.when(cb.equal(piece.get(attribute), value), value.name());
            }
        }
        Expression<String> subStageName = currentName.otherwise(defaultValue.name());

        Subquery<LocalDateTime> sub = query.subquery(LocalDateTime.class);
        Root<PieceSubStageHistory> history = sub.from(PieceSubStageHistory.class);
        sub.select(cb.function("max", LocalDateTime.class, history.get("changedAt")));
        sub.where(
                cb.equal(history.get("piece"), piece),
                cb.equal(history.get("kind"), kind),
                cb.equal(history.get("subStage"), subStageName)
        );
        return sub;
    }

    private static String likeContains(String raw) {
        return "%" + escapeLike(raw.trim().toLowerCase(Locale.ROOT)) + "%";
    }

    private static String escapeLike(String raw) {
        return raw
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
