package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.PerformanceCriterion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PerformanceCriterionRepository extends JpaRepository<PerformanceCriterion, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    List<PerformanceCriterion> findByPeriodIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(UUID periodId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<PerformanceCriterion> findByIdAndPeriodIdAndDeletedAtIsNull(UUID id, UUID periodId);
}
