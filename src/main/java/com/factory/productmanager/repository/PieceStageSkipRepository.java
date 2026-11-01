package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.PieceStageSkip;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PieceStageSkipRepository extends JpaRepository<PieceStageSkip, UUID> {
    List<PieceStageSkip> findByPieceIdOrderByOccurredAtDesc(UUID pieceId);
}
