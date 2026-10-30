package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.TaskComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {

    @EntityGraph(attributePaths = "author")
    List<TaskComment> findByTaskIdOrderByCreatedAtAscIdAsc(UUID taskId);

    @EntityGraph(attributePaths = "author")
    Optional<TaskComment> findByIdAndTaskId(UUID id, UUID taskId);

    long countByTaskId(UUID taskId);
}
