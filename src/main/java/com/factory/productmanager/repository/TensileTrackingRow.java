package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.TensileNextStage;
import com.factory.productmanager.model.entity.TensileTestStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TensileTrackingRow(UUID pieceId, UUID testId, Integer testIndex, TensileTestStatus status,
                                 LocalDateTime testCreatedAt, LocalDateTime reviewedAt, TensileNextStage nextStage,
                                 BigDecimal durationHours, LocalDateTime dispositionCreatedAt) {}
