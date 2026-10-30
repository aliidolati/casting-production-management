package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.RadiographyReportVersion;
import com.factory.productmanager.model.entity.RadiographyReportVersionKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RadiographyReportVersionRepository extends JpaRepository<RadiographyReportVersion, UUID> {

    /** فقط ستون‌های سبک؛ snapshot (حجیم) در لیست خوانده نمی‌شود */
    interface VersionMeta {
        UUID getId();

        int getVersionNo();

        RadiographyReportVersionKind getKind();

        String getContentHash();

        String getCreatedBy();

        LocalDateTime getCreatedAt();
    }

    List<VersionMeta> findByWorkflowIdOrderByVersionNoAsc(UUID workflowId);

    Optional<RadiographyReportVersion> findByIdAndWorkflowId(UUID id, UUID workflowId);

    @Query("SELECT COALESCE(MAX(v.versionNo), 0) FROM RadiographyReportVersion v WHERE v.pieceId = :pieceId")
    int maxVersionNo(@Param("pieceId") UUID pieceId);
}