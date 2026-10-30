package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.TensileNextStage;
import com.factory.productmanager.model.entity.TensileTestDisposition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TensileTestDispositionRepository extends JpaRepository<TensileTestDisposition, UUID> {

    @Query("""
            SELECT COUNT(d)
            FROM TensileTestDisposition d
            WHERE d.tensileTest.piece.id = :pieceId
              AND d.nextStage = :nextStage
            """)
    long countByPieceIdAndNextStage(@Param("pieceId") UUID pieceId, @Param("nextStage") TensileNextStage nextStage);
}
