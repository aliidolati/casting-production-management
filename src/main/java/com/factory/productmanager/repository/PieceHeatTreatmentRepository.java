package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.PieceHeatTreatment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PieceHeatTreatmentRepository extends JpaRepository<PieceHeatTreatment, UUID> {

    List<PieceHeatTreatment> findByPieceIdOrderByCreatedAtDesc(UUID pieceId);

    @Query("""
        SELECT r FROM PieceHeatTreatment r
        JOIN FETCH r.piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project
        WHERE r.batch.id = :batchId
        ORDER BY r.createdAt ASC
        """)
    List<PieceHeatTreatment> findWithPieceByBatchIdOrderByCreatedAtAsc(@Param("batchId") UUID batchId);

    long countByBatchId(UUID batchId);

    boolean existsByBatchIdAndPieceId(UUID batchId, UUID pieceId);
}
