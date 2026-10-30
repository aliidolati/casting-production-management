package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.TaskChecklistItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskChecklistItemRepository extends JpaRepository<TaskChecklistItem, UUID> {

    @EntityGraph(attributePaths = "completedBy")
    List<TaskChecklistItem> findByTaskIdOrderBySortOrderAscCreatedAtAsc(UUID taskId);

    @EntityGraph(attributePaths = "completedBy")
    Optional<TaskChecklistItem> findByIdAndTaskId(UUID id, UUID taskId);

    long countByTaskId(UUID taskId);

    long countByTaskIdAndCompletedTrue(UUID taskId);

    @Query("SELECT COALESCE(MAX(i.sortOrder), 0) FROM TaskChecklistItem i WHERE i.task.id = :taskId")
    int findMaxSortOrder(@Param("taskId") UUID taskId);

    /**
     * پیشرفت چک‌لیست چند وظیفه با یک کوئری گروهی: [taskId, total, completed].
     * برای کارت‌های لیست استفاده می‌شود تا برای هر وظیفه کوئری جداگانه‌ای زده نشود.
     */
    @Query("""
            SELECT i.task.id, COUNT(i), SUM(CASE WHEN i.completed = true THEN 1 ELSE 0 END)
            FROM TaskChecklistItem i
            WHERE i.task.id IN :taskIds
            GROUP BY i.task.id
            """)
    List<Object[]> summarizeByTaskIds(@Param("taskIds") Collection<UUID> taskIds);
}
