package com.factory.productmanager.task;

import com.factory.productmanager.model.dto.SendToWeldingRequest;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.*;
import com.factory.productmanager.service.DashboardService;
import com.factory.productmanager.service.PieceService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardWeldingReportsIntegrationTest extends TaskTestBase {
    @Autowired private PieceService pieceService;
    @Autowired private DashboardService dashboard;
    @Autowired private PieceWeldingDispatchRepository dispatches;
    @Autowired private RadiographyDefectRepository defects;
    @Autowired private PieceStageHistoryRepository stages;
    @Autowired private PieceSubStageHistoryRepository subStages;
    @Autowired private PieceSubStageProcessDateRepository subDates;
    @Autowired private EntityManager entityManager;

    private Piece piece(PieceStage stage) {
        Piece piece = newPiece("REPORT-" + UUID.randomUUID(), "قطعه آزمایشی", 1);
        piece.setCurrentStage(stage);
        return pieceRepository.saveAndFlush(piece);
    }

    private PieceWeldingDispatch send(Piece piece) {
        var request = new SendToWeldingRequest();
        request.setDefectType("G.P");
        request.setNotes("original");
        pieceService.sendToWeldingStation(piece.getId(), request, admin.getUsername());
        return dispatches.findFirstByPieceIdAndCompletedAtIsNullOrderBySentAtDesc(piece.getId()).orElseThrow();
    }

    @Test
    void editOpenDispatchPreservesMovementOriginAndSendMetadataAndCompletionRoute() throws Exception {
        Piece piece = piece(PieceStage.ROUGH_MACHINING);
        piece.setRoughMachiningSubStage(RoughMachiningSubStage.MACHINING);
        PieceWeldingDispatch dispatch = send(piece);
        LocalDateTime sentAt = dispatch.getSentAt();
        String sentBy = dispatch.getSentBy();
        long historyCount = stages.count();
        User operator = newUser("weld-" + UUID.randomUUID(), Role.OPERATOR, Set.of(Permission.MOVE_PIECE_STAGE));

        call(patch("/api/pieces/{id}/welding-dispatches/{dispatchId}", piece.getId(), dispatch.getId()), operator,
                Map.of("defectType", "OTHER", "defectTypeCustom", "  عیب اصلاح‌شده  ", "notes", "  توضیح جدید  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defectTypeCustom").value("عیب اصلاح‌شده"))
                .andExpect(jsonPath("$.notes").value("توضیح جدید"));
        assertEquals(PieceStage.WELDING, piece.getCurrentStage());
        assertEquals(PieceStage.ROUGH_MACHINING, dispatch.getOriginStage());
        assertEquals(RoughMachiningSubStage.MACHINING, dispatch.getOriginRoughMachiningSubStage());
        assertEquals(sentAt, dispatch.getSentAt());
        assertEquals(sentBy, dispatch.getSentBy());
        assertEquals(historyCount, stages.count());
        assertNull(dispatch.getCompletedAt());

        call(post("/api/pieces/{id}/welding-station/complete", piece.getId()), operator,
                Map.of("destination", "PREVIOUS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentStage").value("ROUGH_MACHINING"));
        assertEquals("OTHER", dispatch.getDefectType());
        assertNotNull(dispatch.getCompletedAt());
        call(patch("/api/pieces/{id}/welding-dispatches/{dispatchId}", piece.getId(), dispatch.getId()), admin,
                Map.of("defectType", "VT")).andExpect(status().isNotFound());
    }

    @Test
    void editRequiresPermissionAndDispatchOwnershipAndValidDefectType() throws Exception {
        Piece piece = piece(PieceStage.ROUGH_MACHINING);
        PieceWeldingDispatch dispatch = send(piece);
        Piece other = piece(PieceStage.ROUGH_MACHINING);
        var payload = Map.of("defectType", "VT");
        call(patch("/api/pieces/{id}/welding-dispatches/{dispatchId}", piece.getId(), dispatch.getId()), worker, payload)
                .andExpect(status().isForbidden());
        call(patch("/api/pieces/{id}/welding-dispatches/{dispatchId}", other.getId(), dispatch.getId()), admin, payload)
                .andExpect(status().isNotFound());
        for (var invalid : List.of(Map.of("defectType", ""), Map.of("defectType", "INVALID"), Map.of("defectType", "OTHER"))) {
            call(patch("/api/pieces/{id}/welding-dispatches/{dispatchId}", piece.getId(), dispatch.getId()), admin, invalid)
                    .andExpect(status().isBadRequest());
        }
        assertEquals("G.P", dispatch.getDefectType());
        assertEquals("original", dispatch.getNotes());
    }

    @Test
    void archivedPieceCannotEditAnOpenDispatch() throws Exception {
        Piece piece = piece(PieceStage.ROUGH_MACHINING);
        PieceWeldingDispatch dispatch = send(piece);
        piece.setArchivedAt(LocalDateTime.now());
        pieceRepository.saveAndFlush(piece);
        call(patch("/api/pieces/{id}/welding-dispatches/{dispatchId}", piece.getId(), dispatch.getId()), admin,
                Map.of("defectType", "VT")).andExpect(status().isBadRequest());
        assertEquals("G.P", dispatch.getDefectType());
    }

    private RadiographyDefect defect(Piece piece) {
        var defect = new RadiographyDefect();
        defect.setPiece(piece);
        defect.setCourse("A");
        defect.setDefectType("G.P");
        defect.setDefectClass("T1");
        defect.setStatus(DefectStatus.NEEDS_WELDING);
        return defects.saveAndFlush(defect);
    }

    @Test
    void editValidatesLinkedDefectsAndCompletionConfirmsOnlyTheNewSelection() throws Exception {
        Piece piece = piece(PieceStage.ROUGH_MACHINING);
        var original = defect(piece);
        var replacement = defect(piece);
        var foreign = defect(piece(PieceStage.ROUGH_MACHINING));
        PieceWeldingDispatch dispatch = send(piece);
        dispatch.getDefects().add(original);
        dispatches.saveAndFlush(dispatch);
        call(patch("/api/pieces/{id}/welding-dispatches/{dispatchId}", piece.getId(), dispatch.getId()), admin,
                Map.of("defectType", "VT", "defectIds", List.of(foreign.getId())))
                .andExpect(status().isBadRequest());
        assertEquals(Set.of(original), dispatch.getDefects());
        replacement.setWeldingConfirmed(true);
        call(patch("/api/pieces/{id}/welding-dispatches/{dispatchId}", piece.getId(), dispatch.getId()), admin,
                Map.of("defectType", "VT", "defectIds", List.of(replacement.getId())))
                .andExpect(status().isBadRequest());
        replacement.setWeldingConfirmed(false);
        call(patch("/api/pieces/{id}/welding-dispatches/{dispatchId}", piece.getId(), dispatch.getId()), admin,
                Map.of("defectType", "VT", "defectIds", List.of(replacement.getId())))
                .andExpect(status().isOk());
        call(post("/api/pieces/{id}/welding-station/complete", piece.getId()), admin,
                Map.of("destination", "SHOT")).andExpect(status().isOk());
        assertFalse(original.isWeldingConfirmed());
        assertTrue(replacement.isWeldingConfirmed());
    }

    private void stage(Piece piece, PieceStage stage, LocalDateTime at) {
        var history = new PieceStageHistory();
        history.setPiece(piece);
        history.setStage(stage);
        stages.saveAndFlush(history);
        entityManager.createQuery("update PieceStageHistory h set h.changedAt = :at where h.id = :id")
                .setParameter("at", at).setParameter("id", history.getId()).executeUpdate();
        entityManager.refresh(history);
    }

    @Test
    void editingTheTypeCanRetainAnAlreadyLinkedDefectConfirmedDuringWelding() throws Exception {
        Piece piece = piece(PieceStage.ROUGH_MACHINING);
        var linked = defect(piece);
        PieceWeldingDispatch dispatch = send(piece);
        dispatch.getDefects().add(linked);
        linked.setWeldingConfirmed(true);
        dispatches.saveAndFlush(dispatch);
        call(patch("/api/pieces/{id}/welding-dispatches/{dispatchId}", piece.getId(), dispatch.getId()), admin,
                Map.of("defectType", "VT", "defectIds", List.of(linked.getId())))
                .andExpect(status().isOk());
        assertEquals(Set.of(linked), dispatch.getDefects());
        assertTrue(linked.isWeldingConfirmed());
    }

    private void subStage(Piece piece, SubStageKind kind, String name, LocalDateTime at) {
        var history = new PieceSubStageHistory();
        history.setPiece(piece);
        history.setKind(kind);
        history.setSubStage(name);
        subStages.saveAndFlush(history);
        entityManager.createQuery("update PieceSubStageHistory h set h.changedAt = :at where h.id = :id")
                .setParameter("at", at).setParameter("id", history.getId()).executeUpdate();
        entityManager.refresh(history);
    }

    @Test
    void readyPiecesAreExcludedFromBothStuckAlertApisAndTotals() throws Exception {
        Piece ready = piece(PieceStage.READY);
        Piece welding = new Piece();
        welding.setPieceDefinition(ready.getPieceDefinition());
        welding.setPieceNumber(2);
        welding.setCurrentStage(PieceStage.WELDING);
        pieceRepository.saveAndFlush(welding);
        LocalDateTime old = LocalDateTime.now().minusDays(15);
        stage(ready, PieceStage.READY, old);
        stage(welding, PieceStage.WELDING, old);
        call(get("/api/dashboard/overview").param("projectId", ready.getPieceDefinition().getProject().getId().toString()), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.stuckPiecesTotal").value(1))
                .andExpect(jsonPath("$.stuckPieces[0].pieceId").value(welding.getId().toString()));
        var alerts = dashboard.getAlerts(7).getStuckPieces();
        assertTrue(alerts.stream().noneMatch(alert -> alert.getPieceId().equals(ready.getId())));
        assertTrue(alerts.stream().anyMatch(alert -> alert.getPieceId().equals(welding.getId())));
    }

    @Test
    void stageReportIncludesAllSubStageKindsWithActualDatesAndCurrentState() throws Exception {
        for (SubStageKind kind : SubStageKind.values()) {
            Piece piece = piece(PieceStage.valueOf(kind.name()));
            String first = switch (kind) {
                case RADIOGRAPHY -> "SHOT";
                case HEAT_TREATMENT -> "STAGE_1";
                case ROUGH_MACHINING -> "DEBURRING";
            };
            String current = switch (kind) {
                case RADIOGRAPHY -> "INTERPRETATION";
                case HEAT_TREATMENT -> "STAGE_2";
                case ROUGH_MACHINING -> "MACHINING";
            };
            if (kind == SubStageKind.RADIOGRAPHY) piece.setRadiographySubStage(RadiographySubStage.INTERPRETATION);
            if (kind == SubStageKind.HEAT_TREATMENT) piece.setHeatTreatmentSubStage(HeatTreatmentSubStage.STAGE_2);
            if (kind == SubStageKind.ROUGH_MACHINING) piece.setRoughMachiningSubStage(RoughMachiningSubStage.MACHINING);
            piece.setProcessDate(LocalDate.of(2026, 1, 12));
            stage(piece, piece.getCurrentStage(), LocalDateTime.of(2026, 2, 1, 9, 0));
            subStage(piece, kind, first, LocalDateTime.of(2026, 2, 1, 10, 0));
            subStage(piece, kind, current, LocalDateTime.of(2026, 2, 2, 10, 0));
            var date = new PieceSubStageProcessDate();
            date.setPiece(piece);
            date.setKind(kind);
            date.setSubStage(first);
            date.setProcessDate(LocalDate.of(2026, 1, 10));
            subDates.saveAndFlush(date);
            var rows = dashboard.getStageOccupancyReport(piece.getPieceDefinition().getProject().getId(),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)).getRows();
            assertEquals(3, rows.size());
            var firstRow = rows.stream().filter(row -> first.equals(row.getSubStage())).findFirst().orElseThrow();
            assertEquals(LocalDate.of(2026, 1, 10).atStartOfDay(), firstRow.getEnteredAt());
            assertEquals(LocalDateTime.of(2026, 2, 2, 10, 0), firstRow.getLeftAt());
            assertFalse(firstRow.isStillInStage());
            var currentRow = rows.stream().filter(row -> current.equals(row.getSubStage())).findFirst().orElseThrow();
            assertEquals(LocalDate.of(2026, 1, 12).atStartOfDay(), currentRow.getEnteredAt());
            assertEquals(kind, currentRow.getSubStageKind());
            assertTrue(currentRow.isStillInStage());
            assertNull(currentRow.getLeftAt());
            call(get("/api/dashboard/stage-occupancy-report")
                    .param("projectId", piece.getPieceDefinition().getProject().getId().toString())
                    .param("dateFrom", "2026-01-10").param("dateTo", "2026-01-10"), admin)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(1))
                    .andExpect(jsonPath("$.rows[0].subStage").value(first));
        }
    }

    @Test
    void stageReportNormalizesLegacyAliasesAndDoesNotMarkHistoricalSubStagesCurrent() {
        Piece piece = piece(PieceStage.TENSILE);
        piece.setRadiographySubStage(RadiographySubStage.SHOT);
        stage(piece, PieceStage.RADIOGRAPHY, LocalDateTime.of(2026, 1, 1, 9, 0));
        subStage(piece, SubStageKind.RADIOGRAPHY, "AWAITING_SHOT", LocalDateTime.of(2026, 1, 1, 10, 0));
        subStage(piece, SubStageKind.RADIOGRAPHY, "SHOT", LocalDateTime.of(2026, 1, 2, 10, 0));
        stage(piece, PieceStage.TENSILE, LocalDateTime.of(2026, 1, 3, 10, 0));
        var rows = dashboard.getStageOccupancyReport(piece.getPieceDefinition().getProject().getId(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)).getRows().stream()
                .filter(row -> row.getSubStage() != null).toList();
        assertEquals(1, rows.size());
        assertEquals("SHOT", rows.get(0).getSubStage());
        assertEquals(LocalDateTime.of(2026, 1, 2, 10, 0), rows.get(0).getEnteredAt());
        assertEquals(LocalDateTime.of(2026, 1, 3, 10, 0), rows.get(0).getLeftAt());
        assertFalse(rows.get(0).isStillInStage());
    }
}
