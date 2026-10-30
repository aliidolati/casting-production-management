package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.RadiographyReportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RadiographyReportTemplateRepository extends JpaRepository<RadiographyReportTemplate, UUID> {

    /** قالب‌های یک ردیف قطعه، همراه لاین‌ها تا رندر جدول یک کوئری بیشتر نبَرد */
    @Query("""
        SELECT DISTINCT t FROM RadiographyReportTemplate t
        LEFT JOIN FETCH t.lines
        WHERE t.pieceDefinition.id = :pieceDefinitionId
        ORDER BY t.createdAt ASC
        """)
    List<RadiographyReportTemplate> findByPieceDefinitionIdWithLines(@Param("pieceDefinitionId") UUID pieceDefinitionId);

    boolean existsByPieceDefinitionIdAndNameIgnoreCase(UUID pieceDefinitionId, String name);

    Optional<RadiographyReportTemplate> findByPieceDefinitionIdAndNameIgnoreCase(UUID pieceDefinitionId, String name);

    @Query("""
        SELECT t FROM RadiographyReportTemplate t
        LEFT JOIN FETCH t.lines
        WHERE t.id = :id
        """)
    Optional<RadiographyReportTemplate> findByIdWithLines(@Param("id") UUID id);
}
