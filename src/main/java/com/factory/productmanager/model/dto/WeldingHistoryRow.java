package com.factory.productmanager.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record WeldingHistoryRow(
        UUID pieceId,
        String projectCode,
        String pieceName,
        Integer pieceNumber,
        String defectType,
        String notes,
        String sentBy,
        LocalDateTime sentAt,
        String completedBy,
        LocalDateTime completedAt
) {}
