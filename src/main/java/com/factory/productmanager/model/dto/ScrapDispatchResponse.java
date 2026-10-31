package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ScrapDispatchResponse(UUID id, long version, UUID pieceId, String projectCode,
    String pieceName, Integer pieceNumber, String defectType, String defectTypeCustom,
    String defectLabel, String notes, LocalDateTime sentAt, String sentBy,
    PieceStage originStage, String originSubStage, LocalDateTime reversedAt, String reversedBy,
    LocalDateTime updatedAt, String updatedBy, boolean archived, boolean active, List<UUID> defectIds) { }
