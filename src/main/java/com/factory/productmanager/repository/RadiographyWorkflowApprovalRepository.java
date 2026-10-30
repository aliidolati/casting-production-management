package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.RadiographyWorkflowApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RadiographyWorkflowApprovalRepository extends JpaRepository<RadiographyWorkflowApproval, UUID> {

    List<RadiographyWorkflowApproval> findByWorkflowIdOrderByApprovedAtAsc(UUID workflowId);

    Optional<RadiographyWorkflowApproval> findByIdAndWorkflowId(UUID id, UUID workflowId);
}