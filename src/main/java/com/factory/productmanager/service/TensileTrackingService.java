package com.factory.productmanager.service;

import com.factory.productmanager.model.dto.TensileTrackingInfoResponse;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.TensileTestRepository;
import com.factory.productmanager.repository.TensileTrackingRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** Read-only presentation of the latest relevant decision and current-cycle aging history. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TensileTrackingService {
    private static final Set<TensileNextStage> VISIBLE = EnumSet.of(
            TensileNextStage.NEXT_TENSILE, TensileNextStage.RE_OPERATION, TensileNextStage.AGING);
    private final TensileTestRepository tests;

    public Map<UUID, TensileTrackingInfoResponse> forPieces(List<Piece> pieces) {
        List<UUID> ids = pieces.stream()
                .filter(p -> p.getCurrentStage() == PieceStage.TENSILE && p.getArchivedAt() == null)
                .map(Piece::getId).toList();
        if (ids.isEmpty()) return Map.of();
        Map<UUID, List<TensileTrackingRow>> rows = tests.findTrackingContext(ids).stream()
                .collect(Collectors.groupingBy(TensileTrackingRow::pieceId));
        Map<UUID, TensileTrackingInfoResponse> result = new HashMap<>();
        for (UUID id : ids) {
            List<TensileTrackingRow> pieceRows = rows.getOrDefault(id, List.of()).stream()
                    .sorted(Comparator.comparing(TensileTrackingRow::testIndex)).toList();
            TensileTrackingRow latest = pieceRows.isEmpty() ? null : pieceRows.get(pieceRows.size() - 1);
            TensileTrackingRow decision = pieceRows.stream().filter(r -> r.nextStage() != null)
                    .max(Comparator.comparing(TensileTrackingRow::testIndex)).orElse(null);
            if (latest == null || latest.status() == TensileTestStatus.APPROVED || decision == null
                    || !VISIBLE.contains(decision.nextStage())) continue;
            BigDecimal minutes = decision.nextStage() == TensileNextStage.AGING && decision.durationHours() != null
                    ? decision.durationHours().multiply(BigDecimal.valueOf(60)) : null;
            int lastApprovedTestIndex = pieceRows.stream()
                    .filter(r -> r.status() == TensileTestStatus.APPROVED)
                    .mapToInt(TensileTrackingRow::testIndex)
                    .max().orElse(0);
            List<BigDecimal> agingDurations = pieceRows.stream()
                    .filter(r -> r.testIndex() > lastApprovedTestIndex && r.nextStage() == TensileNextStage.AGING
                            && r.durationHours() != null)
                    .map(r -> r.durationHours().multiply(BigDecimal.valueOf(60)))
                    .toList();
            result.put(id, new TensileTrackingInfoResponse(decision.testId(), decision.nextStage(), minutes, agingDurations));
        }
        return result;
    }
}
