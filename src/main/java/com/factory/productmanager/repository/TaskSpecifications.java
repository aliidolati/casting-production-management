package com.factory.productmanager.repository;

import com.factory.productmanager.model.dto.TaskFilter;
import com.factory.productmanager.model.dto.TaskSort;
import com.factory.productmanager.model.entity.*;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * ساخت شرط‌ها و ترتیب لیست وظایف با JPA Criteria، تا فیلتر، جستجو، مرتب‌سازی و صفحه‌بندی
 * همه در دیتابیس انجام شوند و هیچ‌وقت وظایف زیادی برای فیلتر در حافظه‌ی Java بارگذاری نشوند.
 *
 * مرتب‌سازی عمداً داخل Specification اعمال می‌شود (و نه Sort استاندارد Spring) چون ترتیب
 * «هوشمند» و «بر اساس شدت اولویت» به عبارت CASE نیاز دارند، نه ترتیب الفبایی ستون.
 */
public final class TaskSpecifications {

    /** کاراکتر escape برای LIKE؛ عمداً '!' تا روی MySQL و H2 یکسان رفتار کند */
    private static final char LIKE_ESCAPE = '!';

    private TaskSpecifications() {
    }

    /** وظایفی که کاربر مجاز به دیدنشان است. ادمین: همه. */
    public static Specification<Task> visibleTo(UUID userId, boolean admin, boolean manager) {
        return visibleTo(userId, admin, manager, false);
    }

    public static Specification<Task> visibleTo(UUID userId, boolean admin, boolean manager, boolean meltReviewer) {
        return (root, query, cb) -> {
            if (admin) {
                return cb.conjunction();
            }
            Predicate assigned = cb.equal(root.get("assignedTo").get("id"), userId);
            Predicate ordinary = manager ? cb.or(assigned, cb.equal(root.get("createdBy").get("id"), userId)) : assigned;
            Predicate shared = cb.equal(root.get("taskType"), TaskType.MELT_PASSPORT_APPROVAL);
            Predicate nonShared = cb.or(cb.isNull(root.get("taskType")), cb.not(shared));
            return meltReviewer ? cb.or(shared, cb.and(nonShared, ordinary)) : cb.and(nonShared, ordinary);
        };
    }

    public static Specification<Task> inbox(UUID userId, boolean meltReviewer) {
        return (root, query, cb) -> {
            var assigned = cb.equal(root.get("assignedTo").get("id"), userId);
            return meltReviewer ? cb.or(assigned, cb.equal(root.get("taskType"), TaskType.MELT_PASSPORT_APPROVAL)) : assigned;
        };
    }

    /** همه‌ی فیلترهای کلاینت (بدون شرط دسترسی) */
    public static Specification<Task> matching(TaskFilter f, LocalDateTime now) {
        List<Specification<Task>> parts = new ArrayList<>();

        if (f.statuses() != null && !f.statuses().isEmpty()) {
            parts.add((root, query, cb) -> root.get("status").in(f.statuses()));
        }
        if (f.priorities() != null && !f.priorities().isEmpty()) {
            parts.add((root, query, cb) -> root.get("priority").in(f.priorities()));
        }
        if (f.assigneeId() != null) {
            parts.add((root, query, cb) -> cb.and(cb.equal(root.get("assignedTo").get("id"), f.assigneeId()),
                    cb.or(cb.isNull(root.get("taskType")), cb.notEqual(root.get("taskType"), TaskType.MELT_PASSPORT_APPROVAL))));
        }
        if (f.creatorId() != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("createdBy").get("id"), f.creatorId()));
        }
        if (f.projectId() != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("project").get("id"), f.projectId()));
        }
        if (f.partId() != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("part").get("id"), f.partId()));
        }
        if (f.dueDate() != null) {
            LocalDateTime start = f.dueDate().atStartOfDay();
            parts.add((root, query, cb) -> cb.and(
                    cb.greaterThanOrEqualTo(root.<LocalDateTime>get("dueDate"), start),
                    cb.lessThan(root.<LocalDateTime>get("dueDate"), start.plusDays(1))));
        }
        if (f.dueFrom() != null) {
            LocalDateTime from = f.dueFrom().atStartOfDay();
            parts.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.<LocalDateTime>get("dueDate"), from));
        }
        if (f.dueTo() != null) {
            LocalDateTime to = f.dueTo().plusDays(1).atStartOfDay();
            parts.add((root, query, cb) -> cb.lessThan(root.<LocalDateTime>get("dueDate"), to));
        }
        if (f.overdue() != null) {
            boolean wantOverdue = f.overdue();
            parts.add((root, query, cb) -> {
                Predicate overdue = overduePredicate(root, cb, now);
                return wantOverdue ? overdue : cb.not(overdue);
            });
        }
        if (f.search() != null && !f.search().isBlank()) {
            parts.add(search(f.search().trim()));
        }
        return Specification.allOf(parts);
    }

    private static Specification<Task> search(String term) {
        String pattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            Join<Task, User> assignee = root.join("assignedTo", JoinType.LEFT);
            Join<Task, Project> project = root.join("project", JoinType.LEFT);
            Join<Task, Piece> part = root.join("part", JoinType.LEFT);
            Join<Piece, PieceDefinition> definition = part.join("pieceDefinition", JoinType.LEFT);

            return cb.or(
                    like(cb, root.<String>get("title"), pattern),
                    like(cb, root.<String>get("description"), pattern),
                    like(cb, project.<String>get("code"), pattern),
                    like(cb, project.<String>get("name"), pattern),
                    like(cb, definition.<String>get("pieceName"), pattern),
                    cb.like(part.<Integer>get("pieceNumber").as(String.class), pattern, LIKE_ESCAPE),
                    like(cb, assignee.<String>get("fullName"), pattern),
                    like(cb, assignee.<String>get("username"), pattern)
            );
        };
    }

    private static Predicate like(CriteriaBuilder cb, Expression<String> expression, String pattern) {
        return cb.like(cb.lower(expression), pattern, LIKE_ESCAPE);
    }

    static String escapeLike(String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    /** وظیفه‌ی باز + مهلت گذشته. «تأخیر» هرگز ذخیره نمی‌شود و همیشه از همین شرط می‌آید. */
    private static Predicate overduePredicate(Root<Task> root, CriteriaBuilder cb, LocalDateTime now) {
        return cb.and(
                root.get("status").in(TaskStatus.OPEN),
                cb.isNotNull(root.get("dueDate")),
                cb.lessThan(root.<LocalDateTime>get("dueDate"), now)
        );
    }

    /**
     * ترتیب لیست. روی کوئری شمارش (count) اعمال نمی‌شود.
     * همیشه id آخرین کلید است تا صفحه‌بندی پایدار باشد.
     */
    public static Specification<Task> orderedBy(TaskSort sort, boolean ascending, LocalDateTime now) {
        return (root, query, cb) -> {
            Class<?> resultType = query.getResultType();
            if (Long.class.equals(resultType) || long.class.equals(resultType)) {
                return null;
            }

            Expression<LocalDateTime> due = root.get("dueDate");
            List<Order> orders = new ArrayList<>();

            switch (sort) {
                case SMART -> {
                    Expression<Integer> rank = cb.<Integer>selectCase()
                            .when(root.get("status").in(TaskStatus.COMPLETED, TaskStatus.CANCELLED), 5)
                            .when(overduePredicate(root, cb, now), 0)
                            .when(cb.equal(root.get("priority"), TaskPriority.CRITICAL), 1)
                            .when(cb.equal(root.get("priority"), TaskPriority.HIGH), 2)
                            .when(cb.isNotNull(due), 3)
                            .otherwise(4);
                    orders.add(cb.asc(rank));
                    orders.add(cb.asc(nullsLastFlag(cb, due)));
                    orders.add(cb.asc(due));
                    orders.add(cb.desc(root.get("createdAt")));
                }
                case DUE_DATE -> {
                    orders.add(cb.asc(nullsLastFlag(cb, due)));
                    orders.add(ascending ? cb.asc(due) : cb.desc(due));
                }
                case PRIORITY -> {
                    Expression<Integer> severity = cb.<Integer>selectCase()
                            .when(cb.equal(root.get("priority"), TaskPriority.CRITICAL), TaskPriority.CRITICAL.getSeverity())
                            .when(cb.equal(root.get("priority"), TaskPriority.HIGH), TaskPriority.HIGH.getSeverity())
                            .when(cb.equal(root.get("priority"), TaskPriority.MEDIUM), TaskPriority.MEDIUM.getSeverity())
                            .otherwise(TaskPriority.LOW.getSeverity());
                    orders.add(ascending ? cb.asc(severity) : cb.desc(severity));
                    orders.add(cb.asc(nullsLastFlag(cb, due)));
                    orders.add(cb.asc(due));
                }
                case CREATED -> orders.add(ascending ? cb.asc(root.get("createdAt")) : cb.desc(root.get("createdAt")));
                case UPDATED -> orders.add(ascending ? cb.asc(root.get("updatedAt")) : cb.desc(root.get("updatedAt")));
            }
            orders.add(cb.asc(root.get("id")));
            query.orderBy(orders);
            return null;
        };
    }

    /** 0 برای دارای مهلت و 1 برای بدون مهلت؛ تا وظایف بدون مهلت همیشه آخر بیایند (در MySQL و H2 یکسان) */
    private static Expression<Integer> nullsLastFlag(CriteriaBuilder cb, Expression<LocalDateTime> due) {
        return cb.<Integer>selectCase().when(cb.isNull(due), 1).otherwise(0);
    }
}
