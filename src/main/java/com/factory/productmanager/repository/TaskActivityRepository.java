package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.TaskActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TaskActivityRepository extends JpaRepository<TaskActivity, UUID> {

    @EntityGraph(attributePaths = "actor")
    Page<TaskActivity> findByTaskId(UUID taskId, Pageable pageable);
}
