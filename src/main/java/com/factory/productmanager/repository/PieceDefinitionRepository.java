package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.PieceDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
@Repository
public interface PieceDefinitionRepository extends JpaRepository<PieceDefinition, UUID> {
    boolean existsByProjectIdAndRowNum(UUID projectId, Integer rowNum);

    boolean existsByProjectIdAndRowNumAndIdNot(UUID projectId, Integer rowNum, UUID id);

    List<PieceDefinition> findByProjectId(UUID projectId);

    long countByProjectId(UUID projectId);
}
