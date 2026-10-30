package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.Task;
import com.factory.productmanager.model.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {
    List<Task> findByWorkflowIdAndTaskType(UUID workflowId, com.factory.productmanager.model.entity.TaskType taskType);

    /**
     * ارتباط‌های to-one که در هر ردیف لیست لازم‌اند (مسئول، سازنده، پروژه، قطعه) همراه با خود کوئری
     * (fetch join) خوانده می‌شوند تا N+1 رخ ندهد. کالکشن‌ها (نظرات، چک‌لیست، تاریخچه) هرگز اینجا نمی‌آیند.
     */
    @Override
    @EntityGraph(attributePaths = {
            "createdBy", "assignedTo", "project",
            "part", "part.pieceDefinition", "part.pieceDefinition.project"
    })
    Page<Task> findAll(Specification<Task> spec, Pageable pageable);

    @EntityGraph(attributePaths = {
            "createdBy", "assignedTo", "project",
            "part", "part.pieceDefinition", "part.pieceDefinition.project"
    })
    Optional<Task> findDetailedById(UUID id);

    boolean existsByCreatedByIdOrAssignedToId(UUID createdById, UUID assignedToId);

    // ---------- آمار (همه در سطح دیتابیس؛ هیچ وظیفه‌ای در حافظه شمارش نمی‌شود) ----------

    /** تعداد وظایف یک مسئول به تفکیک وضعیت: [status, count] */
    @Query("SELECT t.status, COUNT(t) FROM Task t WHERE t.assignedTo.id = :assigneeId AND (t.taskType IS NULL OR t.taskType <> com.factory.productmanager.model.entity.TaskType.MELT_PASSPORT_APPROVAL) GROUP BY t.status")
    List<Object[]> countGroupedByStatus(@Param("assigneeId") UUID assigneeId);

    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.assignedTo.id = :assigneeId
              AND (t.taskType IS NULL OR t.taskType <> com.factory.productmanager.model.entity.TaskType.MELT_PASSPORT_APPROVAL)
              AND t.status IN :openStatuses
              AND t.dueDate IS NOT NULL
              AND t.dueDate < :now
            """)
    long countOverdue(
            @Param("assigneeId") UUID assigneeId,
            @Param("openStatuses") Collection<TaskStatus> openStatuses,
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.assignedTo.id = :assigneeId
              AND (t.taskType IS NULL OR t.taskType <> com.factory.productmanager.model.entity.TaskType.MELT_PASSPORT_APPROVAL)
              AND t.status IN :openStatuses
              AND t.dueDate >= :from
              AND t.dueDate < :to
            """)
    long countDueBetween(
            @Param("assigneeId") UUID assigneeId,
            @Param("openStatuses") Collection<TaskStatus> openStatuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // ---------- یادآوری مهلت (صفحه‌به‌صفحه، نه یک‌جا) ----------

    @Query("""
            SELECT t FROM Task t
            JOIN FETCH t.assignedTo
            WHERE t.status IN :openStatuses
              AND (t.taskType IS NULL OR t.taskType <> com.factory.productmanager.model.entity.TaskType.MELT_PASSPORT_APPROVAL)
              AND t.dueDate >= :since
              AND t.dueDate < :horizon
            ORDER BY t.dueDate ASC, t.id ASC
            """)
    Slice<Task> findOpenDueBetween(
            @Param("openStatuses") Collection<TaskStatus> openStatuses,
            @Param("since") LocalDateTime since,
            @Param("horizon") LocalDateTime horizon,
            Pageable pageable
    );
}
