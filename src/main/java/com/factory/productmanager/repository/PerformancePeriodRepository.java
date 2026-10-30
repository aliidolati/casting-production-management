package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.PerformancePeriod;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface PerformancePeriodRepository extends JpaRepository<PerformancePeriod, UUID> {
    Page<PerformancePeriod> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);
    Optional<PerformancePeriod> findByIdAndUserId(UUID id, UUID userId);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from PerformancePeriod p where p.id = :id and p.user.id = :userId")
    Optional<PerformancePeriod> readOwned(@Param("id") UUID id, @Param("userId") UUID userId);
    boolean existsByUserId(UUID userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PerformancePeriod> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PerformancePeriod> findByOpenUserKey(String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PerformancePeriod p where p.id = :id and p.user.id = :userId")
    Optional<PerformancePeriod> lockOwned(@Param("id") UUID id, @Param("userId") UUID userId);
}
