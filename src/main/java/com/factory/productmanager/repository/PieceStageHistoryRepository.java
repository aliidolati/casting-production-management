package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.PieceStageHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PieceStageHistoryRepository extends JpaRepository<PieceStageHistory, UUID> {
    List<PieceStageHistory> findByPieceIdOrderByChangedAtDesc(UUID pieceId);

    List<PieceStageHistory> findByPiece_IdInAndStageOrderByChangedAtAsc(Collection<UUID> pieceIds, PieceStage stage);

    List<PieceStageHistory> findByPiece_IdInOrderByChangedAtAsc(Collection<UUID> pieceIds);

    Optional<PieceStageHistory> findByIdAndPiece_Id(UUID id, UUID pieceId);

    /**
     * آخرین زمان تغییر وضعیت هر قطعه؛ برای تشخیص قطعاتی که مدت زیادی در یک مرحله گیر کرده‌اند استفاده می‌شود.
     * اگر قطعه‌ای هنوز هیچ تغییر وضعیتی نداشته باشد (هنوز در NOT_STARTED است)، در این نتیجه ظاهر نمی‌شود.
     */
    @Query("SELECT h.piece.id, MAX(h.changedAt) FROM PieceStageHistory h GROUP BY h.piece.id")
    List<Object[]> findLatestChangedAtGroupedByPieceId();

    @Query("""
        SELECT h.piece.id, MAX(h.changedAt)
        FROM PieceStageHistory h
        WHERE h.piece.id IN :pieceIds
        GROUP BY h.piece.id
        """)
    List<Object[]> findLatestChangedAtGroupedByPieceIdIn(@Param("pieceIds") Collection<UUID> pieceIds);

    @Query("""
        SELECT h FROM PieceStageHistory h
        JOIN FETCH h.piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project
        WHERE h.stage = :stage
          AND h.changedAt >= :from
          AND h.changedAt < :to
        """)
    List<PieceStageHistory> findByStageBetween(
            @Param("stage") PieceStage stage,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
        SELECT h FROM PieceStageHistory h
        JOIN FETCH h.piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project proj
        WHERE h.stage = :stage
          AND h.changedAt >= :from
          AND h.changedAt < :to
          AND proj.id = :projectId
        """)
    List<PieceStageHistory> findByStageBetweenAndProject(
            @Param("stage") PieceStage stage,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("projectId") UUID projectId
    );

    @Query("""
        SELECT COUNT(DISTINCT h.piece.id)
        FROM PieceStageHistory h
        WHERE h.stage = :stage
          AND h.changedAt >= :from
          AND h.changedAt < :to
        """)
    long countDistinctPiecesByStageBetween(
            @Param("stage") PieceStage stage,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
        SELECT COUNT(DISTINCT h.piece.id)
        FROM PieceStageHistory h
        WHERE h.stage = :stage
          AND h.changedAt >= :from
          AND h.changedAt < :to
          AND h.piece.pieceDefinition.project.id = :projectId
        """)
    long countDistinctPiecesByStageBetweenAndProject(
            @Param("stage") PieceStage stage,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("projectId") UUID projectId
    );

    /** اولین زمان تغییر وضعیت هر قطعه (یک ردیف به‌ازای هر قطعه)؛ جایگزین findAll() برای محاسبهٔ زمان چرخه */
    @Query("SELECT h.piece.id, MIN(h.changedAt) FROM PieceStageHistory h GROUP BY h.piece.id")
    List<Object[]> findEarliestChangedAtGroupedByPieceId();

    @Query("""
        SELECT COUNT(h)
        FROM PieceStageHistory h
        WHERE h.stage = :stage
          AND h.changedAt >= :from
          AND h.changedAt < :to
        """)
    long countByStageBetween(
            @Param("stage") PieceStage stage,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
        SELECT COUNT(h)
        FROM PieceStageHistory h
        WHERE h.stage = :stage
          AND h.changedAt >= :from
          AND h.changedAt < :to
          AND h.piece.pieceDefinition.project.id = :projectId
        """)
    long countByStageBetweenAndProject(
            @Param("stage") PieceStage stage,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("projectId") UUID projectId
    );

    /** فقط (شناسهٔ قطعه، زمان ورود) برای ورودهای یک مرحله در بازه؛ بدون JOIN FETCH و بدون لود شدن Entity */
    @Query("""
        SELECT h.piece.id, h.changedAt
        FROM PieceStageHistory h
        WHERE h.stage = :stage
          AND h.changedAt >= :from
          AND h.changedAt < :to
        """)
    List<Object[]> findPieceIdAndChangedAtByStageBetween(
            @Param("stage") PieceStage stage,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
        SELECT h.piece.id, h.changedAt
        FROM PieceStageHistory h
        WHERE h.stage = :stage
          AND h.changedAt >= :from
          AND h.changedAt < :to
          AND h.piece.pieceDefinition.project.id = :projectId
        """)
    List<Object[]> findPieceIdAndChangedAtByStageBetweenAndProject(
            @Param("stage") PieceStage stage,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("projectId") UUID projectId
    );

    /** جدیدترین تغییرها در بازه؛ محدودسازی (LIMIT) در خود SQL با Pageable انجام می‌شود */
    @Query("""
        SELECT h FROM PieceStageHistory h
        JOIN FETCH h.piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project
        WHERE h.changedAt >= :from
          AND h.changedAt < :to
        ORDER BY h.changedAt DESC
        """)
    List<PieceStageHistory> findRecentWithPieceDetails(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    @Query("""
        SELECT h FROM PieceStageHistory h
        JOIN FETCH h.piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project proj
        WHERE proj.id = :projectId
          AND h.changedAt >= :from
          AND h.changedAt < :to
        ORDER BY h.changedAt DESC
        """)
    List<PieceStageHistory> findRecentWithPieceDetailsByProject(
            @Param("projectId") UUID projectId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    /**
     * شناسه قطعاتی که در بازهٔ زمانی وارد مرحله‌ی مشخص (مثلاً ذوب‌ریزی/CASTING) شده‌اند.
     */
    @Query("""
        SELECT DISTINCT h.piece.id
        FROM PieceStageHistory h
        WHERE h.piece.pieceDefinition.project.id = :projectId
          AND h.stage = :stage
          AND h.changedAt >= :from
          AND h.changedAt < :to
          AND (:pieceDefinitionId IS NULL OR h.piece.pieceDefinition.id = :pieceDefinitionId)
        """)
    List<UUID> findDistinctPieceIdsByStageEntry(
            @Param("projectId") UUID projectId,
            @Param("stage") PieceStage stage,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("pieceDefinitionId") UUID pieceDefinitionId
    );

}
