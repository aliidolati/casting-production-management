package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.HeatTreatmentBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HeatTreatmentBatchRepository extends JpaRepository<HeatTreatmentBatch, UUID> {

    Optional<HeatTreatmentBatch> findByBatchCode(String batchCode);

    boolean existsByBatchCode(String batchCode);

    /**
     * محموله‌های ثبت‌شده از یک زمان به بعد. فهرست پیشنهاد در فرم ثبت فقط
     * محموله‌های امروز را نشان می‌دهد، وگرنه بعد از چند ماه به فهرستی صدتایی
     * و بی‌استفاده تبدیل می‌شود.
     */
    List<HeatTreatmentBatch> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(LocalDateTime from);

    /** آخرین محموله‌ی ثبت‌شده؛ مبنای پیشنهاد کد بعدی */
    Optional<HeatTreatmentBatch> findTopByOrderByCreatedAtDesc();
}
