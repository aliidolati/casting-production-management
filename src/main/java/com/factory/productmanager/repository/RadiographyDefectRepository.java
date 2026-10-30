package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.DefectStatus;
import com.factory.productmanager.model.entity.RadiographyDefect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RadiographyDefectRepository extends JpaRepository<RadiographyDefect, UUID> {
    /** Serialize switching the same chain between a second defect and a reshoot result. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM RadiographyDefect d WHERE d.id = :id")
    Optional<RadiographyDefect> findForUpdateById(@Param("id") UUID id);

    List<RadiographyDefect> findByPieceIdOrderByCreatedAtAsc(UUID pieceId);

    /** عیب دوم یک زنجیره (هر عیب اول حداکثر یکی دارد) */
    Optional<RadiographyDefect> findByParentDefectId(UUID parentDefectId);

    @Query("""
        SELECT COUNT(d) FROM RadiographyDefect d
        WHERE d.reportLine.report.id = :reportId
        """)
    long countByReportId(@Param("reportId") UUID reportId);

    List<RadiographyDefect> findByPieceIdAndIdIn(UUID pieceId, Collection<UUID> ids);

    List<RadiographyDefect> findByPieceIdAndStatusAndWeldingConfirmedFalseOrderByCreatedAtAsc(
            UUID pieceId,
            DefectStatus status
    );

    void deleteByPieceId(UUID pieceId);

    /** شمارش عیوب نیازمند جوش که هنوز تایید نشده‌اند */
    @Query("""
        SELECT COUNT(d) FROM RadiographyDefect d
        WHERE d.piece.id = :pieceId AND d.status = :status AND d.weldingConfirmed = false
        """)
    long countOpenByPieceIdAndStatus(@Param("pieceId") UUID pieceId, @Param("status") DefectStatus status);

    @Query("""
        SELECT d.piece.id, COUNT(d) FROM RadiographyDefect d
        WHERE d.status = :status AND d.weldingConfirmed = false
        GROUP BY d.piece.id
        """)
    List<Object[]> countOpenDefectsGroupedByPieceIdAndStatus(@Param("status") DefectStatus status);

    @Query("""
        SELECT d.piece.id, COUNT(d) FROM RadiographyDefect d
        WHERE d.status = :status AND d.weldingConfirmed = false
          AND d.piece.id IN :pieceIds
        GROUP BY d.piece.id
        """)
    List<Object[]> countOpenDefectsGroupedByPieceIdInAndStatus(
            @Param("pieceIds") Collection<UUID> pieceIds,
            @Param("status") DefectStatus status
    );

    /**
     * عیوب نیازمند جوش تاییدنشده، همراه قطعه/تعریف/پروژه برای ویجت هشدار داشبورد.
     */
    @Query("""
        SELECT d FROM RadiographyDefect d
        JOIN FETCH d.piece p
        JOIN FETCH p.pieceDefinition pd
        JOIN FETCH pd.project
        WHERE d.status = :status AND d.weldingConfirmed = false
          AND p.archivedAt IS NULL
        ORDER BY p.id, d.createdAt
        """)
    List<RadiographyDefect> findOpenByStatusWithPieceDetails(@Param("status") DefectStatus status);

    /**
     * عیوب یک پروژه برای گزارش تجمیعی.
     */
    @Query("""
        SELECT d FROM RadiographyDefect d
        JOIN FETCH d.piece p
        JOIN FETCH p.pieceDefinition pd
        JOIN FETCH pd.project proj
        WHERE proj.id = :projectId
          AND p.archivedAt IS NULL
        ORDER BY pd.rowNum ASC, d.defectType ASC, d.createdAt ASC
        """)
    List<RadiographyDefect> findAllForProjectReport(@Param("projectId") UUID projectId);
}
