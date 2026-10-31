package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.PieceScrapDispatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PieceScrapDispatchRepository extends JpaRepository<PieceScrapDispatch, UUID> {
    List<PieceScrapDispatch> findByPieceIdOrderBySentAtDesc(UUID pieceId);
    Optional<PieceScrapDispatch> findByActivePieceKey(UUID pieceId);
    List<PieceScrapDispatch> findByActivePieceKeyIn(Collection<UUID> pieceIds);
}
