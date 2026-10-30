package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.RadiographyWorkflow;
import com.factory.productmanager.model.entity.RadiographySubStage;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RadiographyWorkflowRepository extends JpaRepository<RadiographyWorkflow, UUID> {

    /** گردش‌کار باز یک قطعه (حداکثر یکی؛ با ستون یکتای open_key تضمین می‌شود) */
    @EntityGraph(attributePaths = {"interpreter", "assignedBy", "interpreterTask", "managerTask"})
    Optional<RadiographyWorkflow> findByOpenKey(String openKey);

    boolean existsByOpenKey(String openKey);

    @EntityGraph(attributePaths = {"interpreter", "assignedBy", "interpreterTask", "managerTask"})
    Optional<RadiographyWorkflow> findFirstByPieceIdOrderByCreatedAtDesc(UUID pieceId);

    @EntityGraph(attributePaths = {
            "interpreter", "assignedBy", "interpreterTask", "managerTask",
            "piece", "piece.pieceDefinition", "piece.pieceDefinition.project"
    })
    Optional<RadiographyWorkflow> findDetailedById(UUID id);

    /** قفل نوشتن: دو درخواست هم‌زمان روی یک گردش‌کار پشت سر هم اجرا می‌شوند */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM RadiographyWorkflow w WHERE w.id = :id")
    Optional<RadiographyWorkflow> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"interpreter"})
    List<RadiographyWorkflow> findByPieceIdInAndOpenKeyIsNotNull(Collection<UUID> pieceIds);

    /** صف تفسیر: قطعات فعال در رادیوگرافی/تفسیر، با جستجوی اختیاری روی نام قطعه، شماره و کد پروژه */
    @Query(value = """
            SELECT p FROM Piece p
            JOIN FETCH p.pieceDefinition d
            JOIN FETCH d.project pr
            WHERE p.currentStage = com.factory.productmanager.model.entity.PieceStage.RADIOGRAPHY
              AND p.radiographySubStage = :subStage
              AND p.archivedAt IS NULL
              AND (:q IS NULL OR LOWER(d.pieceName) LIKE :q ESCAPE '!'
                   OR LOWER(pr.code) LIKE :q ESCAPE '!'
                   OR CAST(p.pieceNumber AS string) LIKE :q ESCAPE '!')
              ORDER BY pr.code, d.pieceName, p.pieceNumber
            """,
            countQuery = """
            SELECT COUNT(p) FROM Piece p
            JOIN p.pieceDefinition d
            JOIN d.project pr
            WHERE p.currentStage = com.factory.productmanager.model.entity.PieceStage.RADIOGRAPHY
              AND p.radiographySubStage = :subStage
              AND p.archivedAt IS NULL
              AND (:q IS NULL OR LOWER(d.pieceName) LIKE :q ESCAPE '!'
                   OR LOWER(pr.code) LIKE :q ESCAPE '!'
                   OR CAST(p.pieceNumber AS string) LIKE :q ESCAPE '!')
            """)
    Page<Piece> findQueuePieces(
            @Param("subStage") RadiographySubStage subStage,
            @Param("q") String likePattern,
            Pageable pageable
    );
}