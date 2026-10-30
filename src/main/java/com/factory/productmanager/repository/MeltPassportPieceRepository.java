package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.MeltPassportPiece;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MeltPassportPieceRepository extends JpaRepository<MeltPassportPiece, UUID> {
    Optional<MeltPassportPiece> findByActivePieceKey(UUID pieceId);
    List<MeltPassportPiece> findByPiece_IdOrderByPassport_CreatedAtDesc(UUID pieceId);
}
