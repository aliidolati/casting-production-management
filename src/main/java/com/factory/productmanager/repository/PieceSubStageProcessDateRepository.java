package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.PieceSubStageProcessDate;
import com.factory.productmanager.model.entity.SubStageKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PieceSubStageProcessDateRepository extends JpaRepository<PieceSubStageProcessDate, UUID> {
    Optional<PieceSubStageProcessDate> findByPiece_IdAndKindAndSubStage(UUID pieceId, SubStageKind kind, String subStage);

    List<PieceSubStageProcessDate> findByPiece_Id(UUID pieceId);

    List<PieceSubStageProcessDate> findByPiece_IdIn(Collection<UUID> pieceIds);
}
