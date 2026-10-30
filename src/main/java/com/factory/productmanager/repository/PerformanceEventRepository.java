package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.PerformanceEvent;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PerformanceEventRepository extends JpaRepository<PerformanceEvent, UUID> {
    Page<PerformanceEvent> findByPeriodIdOrderByOccurredAtDescIdDesc(UUID periodId, Pageable pageable);
}
