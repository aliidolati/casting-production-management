package com.factory.productmanager.repository;
import com.factory.productmanager.model.entity.DrawingRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;
public interface DrawingRevisionRepository extends JpaRepository<DrawingRevision,UUID>{
 List<DrawingRevision> findByDefinitionIdOrderByRevisionNumberDesc(UUID definitionId);
 Optional<DrawingRevision> findFirstByDefinitionIdOrderByRevisionNumberDesc(UUID definitionId);
 Optional<DrawingRevision> findFirstByDefinitionIdAndRelativePathOrderByRevisionNumberDesc(UUID definitionId,String relativePath);
 Page<DrawingRevision> findByDefinitionId(UUID definitionId,Pageable pageable);
 Optional<DrawingRevision> findByIdAndDefinitionId(UUID id,UUID definitionId);
}
