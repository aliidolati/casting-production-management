package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.PieceSubStageHistory;
import com.factory.productmanager.model.entity.SubStageKind;
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
public interface PieceSubStageHistoryRepository extends JpaRepository<PieceSubStageHistory, UUID> {
    List<PieceSubStageHistory> findByPieceIdOrderByChangedAtDesc(UUID pieceId);

    List<PieceSubStageHistory> findByPiece_IdInOrderByChangedAtAsc(Collection<UUID> pieceIds);

    Optional<PieceSubStageHistory> findByIdAndPiece_Id(UUID id, UUID pieceId);

    Optional<PieceSubStageHistory> findFirstByPiece_IdAndKindAndSubStageOrderByChangedAtDesc(
            UUID pieceId,
            SubStageKind kind,
            String subStage
    );

    @Query("""
        SELECT h.piece.id, h.kind, h.subStage, MAX(h.changedAt)
        FROM PieceSubStageHistory h
        WHERE h.piece.id IN :pieceIds
        GROUP BY h.piece.id, h.kind, h.subStage
        """)
    List<Object[]> findLatestChangedAtGroupedByPieceKindAndSubStage(@Param("pieceIds") Collection<UUID> pieceIds);

    @Query("""
        SELECT h.piece.id, MAX(h.changedAt)
        FROM PieceSubStageHistory h
        WHERE h.kind = :kind AND h.subStage = :subStage
        GROUP BY h.piece.id
        """)
    List<Object[]> findLatestChangedAtByKindAndSubStage(
            @Param("kind") SubStageKind kind,
            @Param("subStage") String subStage
    );

    @Query("""
        SELECT COUNT(DISTINCT h.piece.id)
        FROM PieceSubStageHistory h
        WHERE h.kind = :kind
          AND h.subStage = :subStage
          AND h.changedAt >= :from
          AND h.changedAt < :to
        """)
    long countDistinctPiecesBySubStageBetween(
            @Param("kind") SubStageKind kind,
            @Param("subStage") String subStage,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
        SELECT COUNT(DISTINCT h.piece.id)
        FROM PieceSubStageHistory h
        WHERE h.kind = :kind
          AND h.subStage = :subStage
          AND h.changedAt >= :from
          AND h.changedAt < :to
          AND h.piece.pieceDefinition.project.id = :projectId
        """)
    long countDistinctPiecesBySubStageBetweenAndProject(
            @Param("kind") SubStageKind kind,
            @Param("subStage") String subStage,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("projectId") UUID projectId
    );
}
