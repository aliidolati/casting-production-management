package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.PieceNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PieceNoteRepository extends JpaRepository<PieceNote, UUID> {
    List<PieceNote> findByPieceIdOrderByCreatedAtDesc(UUID pieceId);

    long countByPieceId(UUID pieceId);
}
