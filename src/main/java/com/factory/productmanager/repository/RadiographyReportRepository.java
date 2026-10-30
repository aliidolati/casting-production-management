package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.RadiographyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RadiographyReportRepository extends JpaRepository<RadiographyReport, UUID> {

    @Query("""
        SELECT DISTINCT r FROM RadiographyReport r
        LEFT JOIN FETCH r.lines l
        LEFT JOIN FETCH l.templateLine
        JOIN FETCH r.template
        WHERE r.piece.id = :pieceId
        ORDER BY r.createdAt ASC
        """)
    List<RadiographyReport> findByPieceIdOrderByCreatedAtAsc(@Param("pieceId") UUID pieceId);

    @Query("""
        SELECT r FROM RadiographyReport r
        LEFT JOIN FETCH r.lines l
        LEFT JOIN FETCH l.templateLine
        JOIN FETCH r.template
        WHERE r.piece.id = :pieceId AND r.template.id = :templateId
        """)
    Optional<RadiographyReport> findByPieceIdAndTemplateId(
            @Param("pieceId") UUID pieceId,
            @Param("templateId") UUID templateId
    );

    long countByTemplateId(UUID templateId);
}
