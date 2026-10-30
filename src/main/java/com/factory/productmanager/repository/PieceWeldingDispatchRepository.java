package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.PieceWeldingDispatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PieceWeldingDispatchRepository extends JpaRepository<PieceWeldingDispatch, UUID> {
    List<PieceWeldingDispatch> findByPieceIdOrderBySentAtDesc(UUID pieceId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<PieceWeldingDispatch> findFirstByPieceIdAndCompletedAtIsNullOrderBySentAtDesc(UUID pieceId);

    @Query("""
        SELECT d FROM PieceWeldingDispatch d
        JOIN FETCH d.piece
        WHERE d.piece.id IN :pieceIds AND d.completedAt IS NULL
        """)
    List<PieceWeldingDispatch> findByPiece_IdInAndCompletedAtIsNull(@Param("pieceIds") Collection<UUID> pieceIds);
}
