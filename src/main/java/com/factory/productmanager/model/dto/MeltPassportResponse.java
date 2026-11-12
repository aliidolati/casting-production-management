package com.factory.productmanager.model.dto;
import com.factory.productmanager.model.entity.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
public record MeltPassportResponse(UUID id, long version, String number, MeltPassportStatus status,
 LocalDate meltDate, Integer furnaceNumber, String ingotType, String primaryBatch, String recycledBatch,
 BigDecimal primaryWeight, BigDecimal recycledWeight, BigDecimal primaryPercent, BigDecimal recycledPercent,
 String injectionOperator, String notes, String additionalNotes, MeltProcessData process,
 MeltPassportRequest.MagnesiumInput magnesium, BigDecimal magnesiumResult, LocalDateTime magnesiumCalculatedAt,
 List<PieceLink> pieces, List<MaterialLine> materials, List<MeltPassportRequest.CheckLine> checks,
 List<Event> events, LocalDateTime createdAt, String createdBy, LocalDateTime updatedAt, String updatedBy,
 LocalDateTime submittedAt, String submittedBy, LocalDateTime approvedAt, String approvedBy,
 LocalDateTime returnedAt, String returnedBy, String correctionReason, boolean editable, boolean reviewable, boolean correctable,
 UUID assignedOperatorId, String assignedOperatorName, boolean assignable, boolean cancellable, boolean deletable, boolean membershipEditable) {
 public record PieceLink(UUID id, String pieceName, String pieceRef, boolean active) {}
 public record MaterialLine(UUID materialId, String materialName, String category, BigDecimal quantity,
  MeltMaterialUnit unit, BigDecimal baseQuantity, String baseUnit, LocalTime addedAt, String batchNumber, String notes) {}
 public record Event(String action, UUID actorId, String actorName, LocalDateTime occurredAt, String reason,
                     JsonNode oldValues, JsonNode newValues) {}
}
