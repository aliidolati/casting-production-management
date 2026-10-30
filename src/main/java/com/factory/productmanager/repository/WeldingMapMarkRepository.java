package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.WeldingMapMark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WeldingMapMarkRepository extends JpaRepository<WeldingMapMark, UUID> {
    List<WeldingMapMark> findByPieceIdOrderByCreatedAtAsc(UUID pieceId);

    @Query("""
            SELECT m FROM WeldingMapMark m
            JOIN FETCH m.piece p
            WHERE p.pieceDefinition.id = :definitionId
              AND p.archivedAt IS NULL
            ORDER BY p.pieceNumber ASC, m.createdAt ASC
            """)
    List<WeldingMapMark> findByPieceDefinitionId(@Param("definitionId") UUID definitionId);
}
