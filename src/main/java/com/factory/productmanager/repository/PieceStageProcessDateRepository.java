package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.PieceStageProcessDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PieceStageProcessDateRepository extends JpaRepository<PieceStageProcessDate, UUID> {
    Optional<PieceStageProcessDate> findByPieceIdAndStage(UUID pieceId, PieceStage stage);

    List<PieceStageProcessDate> findByPiece_IdIn(Collection<UUID> pieceIds);
}
