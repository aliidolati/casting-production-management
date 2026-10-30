package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.RadiographyWorkflowEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RadiographyWorkflowEventRepository extends JpaRepository<RadiographyWorkflowEvent, UUID> {

    List<RadiographyWorkflowEvent> findByWorkflowIdOrderByCreatedAtAsc(UUID workflowId);
}