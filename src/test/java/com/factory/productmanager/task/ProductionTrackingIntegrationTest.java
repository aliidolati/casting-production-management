package com.factory.productmanager.task;

import com.factory.productmanager.model.dto.PieceResponse;
import com.factory.productmanager.model.dto.TrackingPageResponse;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.service.PieceService;
import com.factory.productmanager.repository.*;
import com.factory.productmanager.service.TensileTestService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Read-only tracking regressions against synthetic H2 fixtures. */
class ProductionTrackingIntegrationTest extends TaskTestBase {
    @Autowired private PieceService pieceService;
    @Autowired private EntityManager em;
    @Autowired private TensileTestRepository tensileTests;
    @Autowired private PieceStageHistoryRepository stageHistory;
    @Autowired private PieceStageProcessDateRepository stageDates;
    @Autowired private PieceSubStageHistoryRepository subHistory;
    @Autowired private PieceSubStageProcessDateRepository subDates;
    @Autowired private TensileTestService tensileService;
    private PieceDefinition definition;
    private String code;

    @BeforeEach
    void seedProduction() {
        code = "TRACK-" + UUID.randomUUID().toString().substring(0, 8);
        Piece first = newPiece(code, "Housing 19", 9);
        definition = first.getPieceDefinition();
        for (int number : List.of(19, 90, 109, 219)) {
            addPiece(number, PieceStage.CASTING);
        }
        first.setCurrentStage(PieceStage.CASTING);
        pieceRepository.saveAndFlush(first);
    }

    private Piece addPiece(int number, PieceStage stage) {
        Piece piece = new Piece();
        piece.setPieceDefinition(definition);
        piece.setPieceNumber(number);
        piece.setCurrentStage(stage);
        return pieceRepository.saveAndFlush(piece);
    }

    private TrackingPageResponse tracking(String q, String name, String project, PieceStage stage,
                                                LocalDate from, LocalDate to, int page, int size) {
        return pieceService.listTrackingPieces(q, name, project, stage, from, to,
                null, null, null, "pieceNumber", "asc", page, size, false);
    }

    @Test
    void numericSearchMatchesOnlyTheExactPieceIncludingLocalizedDigitsAndWhitespace() throws Exception {
        for (String query : List.of("9", " 009 ", " ۹ ", "٩", "۰۰۹")) {
            call(get("/api/pieces/tracking").param("q", query).param("projectCode", code), worker)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.items[0].pieceNumber").value(9));
            call(get("/api/pieces/tracking/summary").param("q", query).param("projectCode", code), worker)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(1));
        }
        call(get("/api/pieces/search").param("q", " ۹ "), worker)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].pieceNumber").value(9));
    }

    @Test
    void outOfRangeNumericSearchDoesNotFallBackToKeywords() {
        assertEquals(0, tracking("999999999999999999999", null, code, null, null, null, 0, 50).totalElements());
        assertTrue(pieceService.searchPieces("999999999999999999999").isEmpty());
    }

    @Test
    void generalTextAndDedicatedColumnFiltersKeepPartialMatching() {
        assertEquals(5, tracking("hous", null, code, null, null, null, 0, 50).totalElements());
        assertEquals(5, tracking(null, "19", code.substring(0, code.length() - 2), null, null, null, 0, 50).totalElements());
        assertEquals(5, tracking("  " + code.toLowerCase() + "  ", null, null, null, null, null, 0, 50).totalElements());
    }

    @Test
    void exactSearchRetainsStageDateArchiveAndProjectFilters() {
        Piece archived = addPiece(8, PieceStage.CASTING);
        archived.setArchivedAt(LocalDateTime.now());
        pieceRepository.saveAndFlush(archived);
        assertEquals(0, tracking("8", null, code, null, null, null, 0, 50).totalElements());
        assertEquals(0, tracking("9", null, code, PieceStage.TENSILE, null, null, 0, 50).totalElements());
        assertEquals(0, tracking("9", null, "absent", null, null, null, 0, 50).totalElements());
        assertEquals(1, tracking("9", null, code, PieceStage.CASTING, LocalDate.now(), LocalDate.now(), 0, 50).totalElements());
        assertEquals(0, tracking("9", null, code, null, LocalDate.now().plusDays(1), null, 0, 50).totalElements());
    }

    @Test
    void filteredListsRetainPaginationTotalsAndStableNumericSorting() {
        var first = tracking("hous", null, code, null, null, null, 0, 2);
        var second = tracking("hous", null, code, null, null, null, 1, 2);
        assertEquals(5, first.totalElements());
        assertEquals(3, first.totalPages());
        assertEquals(List.of(9, 19), first.items().stream().map(PieceResponse::getPieceNumber).toList());
        assertEquals(List.of(90, 109), second.items().stream().map(PieceResponse::getPieceNumber).toList());
        var empty = tracking("9", null, code, null, null, null, 1, 1);
        assertEquals(1, empty.totalElements());
        assertTrue(empty.items().isEmpty());
    }

    @Test
    void dashboardScrapCountUsesExistingCurrentStatusAndProjectScope() throws Exception {
        addPiece(2, PieceStage.SCRAPPED);
        Piece archived = addPiece(3, PieceStage.SCRAPPED);
        archived.setArchivedAt(LocalDateTime.now());
        pieceRepository.saveAndFlush(archived);
        newPiece("OTHER-" + UUID.randomUUID(), "Other", 1).setCurrentStage(PieceStage.SCRAPPED);
        pieceRepository.flush();
        call(get("/api/dashboard/overview").param("projectId", definition.getProject().getId().toString())
                        .param("dateFrom", "2000-01-01").param("dateTo", "2000-01-02"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scrappedInPeriod").value(1))
                .andExpect(jsonPath("$.stageCounts.SCRAPPED").value(1))
                .andExpect(jsonPath("$.totalPieces").value(6));
        call(get("/api/dashboard/overview"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scrappedInPeriod").value(2));
    }

    @Test
    void advancedSearchIncludesArchivesInListSummaryAndPaginationWithoutChangingOtherFilters() throws Exception {
        Piece archived = addPiece(8, PieceStage.RADIOGRAPHY);
        archived.setRadiographySubStage(RadiographySubStage.VT);
        archived.setArchivedAt(LocalDateTime.now());
        pieceRepository.saveAndFlush(archived);
        for (String include : List.of("false", "true")) {
            int total = include.equals("true") ? 6 : 5;
            call(get("/api/pieces/tracking").param("projectCode", code).param("includeArchived", include)
                            .param("sortBy", "pieceNumber").param("sortDir", "asc").param("size", "2"), worker)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(total))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.items[0].pieceNumber").value(include.equals("true") ? 8 : 9));
            call(get("/api/pieces/tracking/summary").param("projectCode", code).param("includeArchived", include), worker)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(total))
                    .andExpect(jsonPath("$.stageCounts.RADIOGRAPHY").value(include.equals("true") ? 1 : 0));
        }
        call(get("/api/pieces/tracking/summary").param("projectCode", code), worker)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(5));
        call(get("/api/pieces/tracking").param("projectCode", code).param("includeArchived", "true")
                        .param("sortBy", "pieceNumber").param("sortDir", "asc").param("size", "2").param("page", "2"), worker)
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].pieceNumber").value(109))
                .andExpect(jsonPath("$.items[1].pieceNumber").value(219));
        call(get("/api/pieces/tracking").param("q", "۸").param("projectCode", code)
                        .param("pieceName", "hous").param("stage", "RADIOGRAPHY").param("radiographySubStage", "VT")
                        .param("dateFrom", LocalDate.now().toString()).param("dateTo", LocalDate.now().toString())
                        .param("includeArchived", "true"), worker)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].archivedAt").isNotEmpty());
        for (String[] filter : List.of(new String[]{"projectCode", "ABSENT"}, new String[]{"pieceName", "ABSENT"},
                new String[]{"radiographySubStage", "SHOT"}, new String[]{"dateFrom", LocalDate.now().plusDays(1).toString()})) {
            call(get("/api/pieces/tracking").param("q", "8").param("includeArchived", "true")
                            .param(filter[0], filter[1]), worker)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        }
        assertNotNull(pieceRepository.findById(archived.getId()).orElseThrow().getArchivedAt());
    }

    @Test
    void definitionDurationsCanBeSavedUpdatedAndClearedWithoutBreakingOlderClients() throws Exception {
        var body = new HashMap<String, Object>(Map.of(
                "projectId", definition.getProject().getId(),
                "rowNum", 2,
                "pieceName", "Synthetic RT row",
                "radiographyShotHours", 1.25,
                "radiographyInterpretationHours", 0.5
        ));
        call(post("/api/piece-definitions"), worker, body).andExpect(status().isForbidden());
        String created = call(post("/api/piece-definitions"), admin, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.radiographyShotHours").value(1.25))
                .andExpect(jsonPath("$.radiographyInterpretationHours").value(0.5))
                .andReturn().getResponse().getContentAsString();
        String createdId = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        String url = "/api/piece-definitions/" + createdId;
        body.remove("projectId");
        body.remove("radiographyShotHours");
        body.remove("radiographyInterpretationHours");
        call(put(url), admin, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.radiographyShotHours").value(1.25))
                .andExpect(jsonPath("$.radiographyInterpretationHours").value(0.5));

        body.put("radiographyShotHours", -1);
        call(put(url), admin, body).andExpect(status().isBadRequest());
        body.put("radiographyShotHours", 1.234);
        call(put(url), admin, body).andExpect(status().isBadRequest());
        body.put("radiographyShotHours", null);
        body.put("radiographyInterpretationHours", 0.75);
        call(put(url), admin, body).andExpect(status().isOk());
        em.flush();
        em.clear();
        call(get(url), worker).andExpect(status().isOk())
                .andExpect(jsonPath("$.radiographyShotHours").isEmpty())
                .andExpect(jsonPath("$.radiographyInterpretationHours").value(0.75));
    }

    @Test
    void radiographyBacklogUsesFilteredPiecesAcrossPagesIncludingArchivesAndManagerReview() throws Exception {
        definition.setRadiographyShotHours(new BigDecimal("1.25"));
        definition.setRadiographyInterpretationHours(new BigDecimal("0.75"));
        PieceDefinition otherRow = new PieceDefinition();
        otherRow.setProject(definition.getProject());
        otherRow.setRowNum(2);
        otherRow.setPieceName("Synthetic RT without times");
        definitionRepository.save(otherRow);

        Piece shot = addPiece(11, PieceStage.RADIOGRAPHY);
        shot.setRadiographySubStage(RadiographySubStage.SHOT);
        Piece archived = addPiece(12, PieceStage.RADIOGRAPHY);
        archived.setRadiographySubStage(RadiographySubStage.SHOT);
        archived.setArchivedAt(LocalDateTime.now());
        Piece missingTime = addPiece(13, PieceStage.RADIOGRAPHY);
        missingTime.setPieceDefinition(otherRow);
        missingTime.setRadiographySubStage(RadiographySubStage.SHOT);
        Piece waiting = addPiece(14, PieceStage.RADIOGRAPHY);
        waiting.setRadiographySubStage(RadiographySubStage.INTERPRETATION);
        var workflow = new RadiographyWorkflow();
        workflow.setPiece(waiting);
        workflow.setInterpreter(worker);
        workflow.setAssignedBy(admin);
        workflow.setAssignedAt(LocalDateTime.now());
        workflow.setStatus(RadiographyWorkflowStatus.MANAGER_REVIEW);
        workflow.setOpenKey(waiting.getId().toString());
        em.persist(workflow);
        em.flush();

        for (String page : List.of("0", "1")) {
            call(radiographyTracking("SHOT").param("page", page).param("size", "1"), worker)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.radiographyBacklogHours").value(2.5));
        }
        call(radiographyTracking("SHOT").param("includeArchived", "true"), worker)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.radiographyBacklogHours").value(2.5));
        call(radiographyTracking("SHOT").param("q", "11").param("pieceName", "Housing")
                        .param("dateFrom", LocalDate.now().toString()).param("dateTo", LocalDate.now().toString()), worker)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.radiographyBacklogHours").value(1.25));
        call(radiographyTracking("SHOT").param("q", "13"), worker)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.radiographyBacklogHours").value(0));
        call(radiographyTracking("SHOT").param("dateFrom", LocalDate.now().plusDays(1).toString()), worker)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.radiographyBacklogHours").value(0));
        call(radiographyTracking("INTERPRETATION"), worker).andExpect(status().isOk())
                .andExpect(jsonPath("$.radiographyBacklogHours").value(0.75));
        call(radiographyTracking("VT"), worker).andExpect(status().isOk())
                .andExpect(jsonPath("$.radiographyBacklogHours").doesNotHaveJsonPath());
    }

    private MockHttpServletRequestBuilder radiographyTracking(String subStage) {
        return get("/api/pieces/tracking").param("projectCode", code).param("stage", "RADIOGRAPHY")
                .param("radiographySubStage", subStage);
    }

    @Test
    void scrapRateIncludesArchivedScrapAndDeliveredPiecesAndExcludesAllNotStartedPieces() throws Exception {
        // Other suites can leave synthetic rows in the shared H2 context. Account for that
        // baseline only in the unfiltered assertion; the project-scoped expectations stay exact.
        var baseline = pieceRepository.findAll();
        long previousStarted = baseline.stream().filter(p -> p.getCurrentStage() != PieceStage.NOT_STARTED).count();
        long previousScrapped = baseline.stream().filter(p -> p.getCurrentStage() == PieceStage.SCRAPPED).count();
        addPiece(2, PieceStage.SCRAPPED);
        Piece archivedScrap = addPiece(3, PieceStage.SCRAPPED);
        archivedScrap.setArchivedAt(LocalDateTime.now());
        Piece archivedDelivered = addPiece(4, PieceStage.DELIVERED);
        archivedDelivered.setArchivedAt(LocalDateTime.now());
        addPiece(5, PieceStage.NOT_STARTED);
        Piece archivedNotStarted = addPiece(6, PieceStage.NOT_STARTED);
        archivedNotStarted.setArchivedAt(LocalDateTime.now());
        Piece other = newPiece("OTHER-" + UUID.randomUUID(), "Other", 1);
        other.setCurrentStage(PieceStage.SCRAPPED);
        other.setArchivedAt(LocalDateTime.now());
        pieceRepository.flush();
        // Five casting + one active scrap + one archived scrap + one archived delivery = eight started.
        for (boolean historicalPeriod : List.of(false, true)) {
            var request = get("/api/dashboard/overview").param("projectId", definition.getProject().getId().toString());
            if (historicalPeriod) request.param("dateFrom", "2000-01-01").param("dateTo", "2000-01-02");
            call(request, admin).andExpect(status().isOk())
                    .andExpect(jsonPath("$.scrapRatePercent").value(25.0))
                    .andExpect(jsonPath("$.scrappedInPeriod").value(1))
                    .andExpect(jsonPath("$.totalPieces").value(7));
        }
        call(get("/api/dashboard/overview"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.scrapRatePercent").value(
                        Math.round(1000.0 * (previousScrapped + 3) / (previousStarted + 4)) / 10.0));
    }

    @Test
    void scrapRateHandlesZeroDenominatorZeroScrapAndAllArchivedScrap() throws Exception {
        Piece piece = newPiece("EDGE-" + UUID.randomUUID(), "Edge", 1);
        piece.setCurrentStage(PieceStage.NOT_STARTED);
        pieceRepository.flush();
        String projectId = piece.getPieceDefinition().getProject().getId().toString();
        for (boolean archived : List.of(false, true)) {
            piece.setArchivedAt(archived ? LocalDateTime.now() : null);
            pieceRepository.flush();
            call(get("/api/dashboard/overview").param("projectId", projectId), admin)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.scrapRatePercent").isEmpty());
        }
        piece.setCurrentStage(PieceStage.DELIVERED);
        pieceRepository.flush();
        call(get("/api/dashboard/overview").param("projectId", projectId), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.scrapRatePercent").value(0.0));
        piece.setCurrentStage(PieceStage.SCRAPPED);
        pieceRepository.flush();
        call(get("/api/dashboard/overview").param("projectId", projectId), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.scrapRatePercent").value(100.0));
        call(get("/api/dashboard/overview").param("projectId", UUID.randomUUID().toString()), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.scrapRatePercent").isEmpty());
    }

    private Piece tensilePiece() {
        Piece piece = addPiece(501, PieceStage.TENSILE);
        history(piece, PieceStage.TENSILE, LocalDateTime.of(2026, 1, 1, 10, 0));
        return piece;
    }

    private void history(Piece piece, PieceStage stage, LocalDateTime at) {
        PieceStageHistory row = new PieceStageHistory();
        row.setPiece(piece);
        row.setStage(stage);
        row = stageHistory.saveAndFlush(row);
        em.createQuery("update PieceStageHistory h set h.changedAt = :at where h.id = :id")
                .setParameter("at", at).setParameter("id", row.getId()).executeUpdate();
        em.refresh(row);
    }

    private TensileTest test(Piece piece, int index, TensileTestStatus status, TensileNextStage next, String hours) {
        TensileTest test = new TensileTest();
        test.setPiece(piece);
        test.setTestIndex(index);
        test.setStatus(status);
        if (next != null) {
            var decision = new TensileTestDisposition();
            decision.setTensileTest(test);
            decision.setDispositionDate(LocalDate.of(2026, 1, 2));
            decision.setNextStage(next);
            decision.setInspectionResult(status == TensileTestStatus.APPROVED
                    ? TensileInspectionResult.ACCEPTED : TensileInspectionResult.REJECTED);
            decision.setDurationHours(hours == null ? null : new BigDecimal(hours));
            test.setDisposition(decision);
            test.setReviewedAt(LocalDateTime.of(2026, 1, 2 + (index - 1) * 2, 10, index));
        }
        test = tensileTests.saveAndFlush(test);
        em.createQuery("update TensileTest t set t.createdAt = :at where t.id = :id")
                .setParameter("at", LocalDateTime.of(2026, 1, 2 + (index - 1) * 2, 9, index))
                .setParameter("id", test.getId()).executeUpdate();
        em.refresh(test);
        return test;
    }

    private PieceResponse tracked(Piece piece) {
        return tracking(String.valueOf(piece.getPieceNumber()), null, code, null, null, null, 0, 50).items().get(0);
    }

    private void heatReturn(Piece piece) {
        history(piece, PieceStage.HEAT_TREATMENT, LocalDateTime.of(2026, 1, 2, 11, 0));
        history(piece, PieceStage.TENSILE, LocalDateTime.of(2026, 1, 3, 10, 0));
    }

    @Test
    void agingDisplaysOnlyPersistedDurationConvertedToMinutesWithoutChangingRecords() {
        Piece piece = tensilePiece();
        TensileTest previous = test(piece, 1, TensileTestStatus.REJECTED, TensileNextStage.AGING, "1.50");
        heatReturn(piece);
        test(piece, 2, TensileTestStatus.PENDING_REVIEW, null, null);
        var info = tracked(piece).getTensileTrackingInfo();
        assertNotNull(info);
        assertEquals(previous.getId(), info.tensileTestId());
        assertEquals(TensileNextStage.AGING, info.nextStage());
        assertEquals(0, new BigDecimal("90").compareTo(info.agingDurationMinutes()));
        assertEquals(new BigDecimal("1.50"), tensileTests.findById(previous.getId()).orElseThrow().getDisposition().getDurationHours());
        assertEquals(PieceStage.TENSILE, pieceRepository.findById(piece.getId()).orElseThrow().getCurrentStage());
        assertEquals(2, tensileTests.findByPieceIdOrderByTestIndexAsc(piece.getId()).size());
    }

    @Test
    void missingAgingDurationIsNotInventedAndCurrentDecisionCanBeShownBeforeAnotherTest() {
        Piece piece = tensilePiece();
        test(piece, 1, TensileTestStatus.REJECTED, TensileNextStage.AGING, null);
        heatReturn(piece);
        var info = tracked(piece).getTensileTrackingInfo();
        assertNotNull(info);
        assertNull(info.agingDurationMinutes());
    }

    @Test
    void repeatTensileAndReoperationUseTheirActualDecisions() {
        Piece piece = tensilePiece();
        test(piece, 1, TensileTestStatus.REJECTED, TensileNextStage.NEXT_TENSILE, "2");
        var info = tracked(piece).getTensileTrackingInfo();
        assertEquals(TensileNextStage.NEXT_TENSILE, info.nextStage());
        assertNull(info.agingDurationMinutes());
        test(piece, 2, TensileTestStatus.REJECTED, TensileNextStage.RE_OPERATION, "3");
        history(piece, PieceStage.HEAT_TREATMENT, LocalDateTime.of(2026, 1, 4, 11, 0));
        history(piece, PieceStage.TENSILE, LocalDateTime.of(2026, 1, 5, 10, 0));
        info = tracked(piece).getTensileTrackingInfo();
        assertEquals(TensileNextStage.RE_OPERATION, info.nextStage());
        assertNull(info.agingDurationMinutes());
    }

    @Test
    void latestApprovedTestAndUnrelatedLatestDecisionHidePreviousAging() {
        Piece piece = tensilePiece();
        test(piece, 1, TensileTestStatus.REJECTED, TensileNextStage.AGING, "1");
        heatReturn(piece);
        test(piece, 2, TensileTestStatus.APPROVED, TensileNextStage.APPROVED, null);
        assertNull(tracked(piece).getTensileTrackingInfo());
        test(piece, 3, TensileTestStatus.PENDING_REVIEW, null, null);
        assertNull(tracked(piece).getTensileTrackingInfo());
        test(piece, 4, TensileTestStatus.REJECTED, TensileNextStage.OTHER, null);
        assertNull(tracked(piece).getTensileTrackingInfo());
    }

    @Test
    void currentDecisionIsVisibleEvenWhenLegacyStageHistoryCannotProveTheReturnPath() {
        Piece piece = tensilePiece();
        test(piece, 1, TensileTestStatus.REJECTED, TensileNextStage.AGING, "1");
        var info = tracked(piece).getTensileTrackingInfo();
        assertNotNull(info);
        assertEquals(TensileNextStage.AGING, info.nextStage());
        assertEquals(0, new BigDecimal("60").compareTo(info.agingDurationMinutes()));
        history(piece, PieceStage.CASTING, LocalDateTime.of(2026, 1, 2, 10, 30));
        heatReturn(piece);
        info = tracked(piece).getTensileTrackingInfo();
        assertNotNull(info);
        assertEquals(TensileNextStage.AGING, info.nextStage());
    }

    @Test
    void agingDurationsBeforeTheLatestApprovedTestAreNotIncludedInTheNewCycle() {
        Piece piece = tensilePiece();
        test(piece, 1, TensileTestStatus.REJECTED, TensileNextStage.AGING, "1");
        heatReturn(piece);
        test(piece, 2, TensileTestStatus.APPROVED, TensileNextStage.APPROVED, null);
        test(piece, 3, TensileTestStatus.REJECTED, TensileNextStage.NEXT_TENSILE, null);
        var info = tracked(piece).getTensileTrackingInfo();
        assertNotNull(info);
        assertEquals(TensileNextStage.NEXT_TENSILE, info.nextStage());
        assertTrue(info.agingDurationsMinutes().isEmpty());
    }

    @Test
    void noContextIsExposedOutsideTensileOrWithoutAPersistedDecision() {
        Piece piece = tensilePiece();
        assertNull(tracked(piece).getTensileTrackingInfo());
        test(piece, 1, TensileTestStatus.REJECTED, TensileNextStage.NEXT_TENSILE, null);
        piece.setCurrentStage(PieceStage.HEAT_TREATMENT);
        pieceRepository.saveAndFlush(piece);
        assertNull(tracked(piece).getTensileTrackingInfo());
    }

    @Test
    void stageHistoryKeepsRecordingTimeOrderAndReturnsExecutionDatesSeparately() {
        Piece piece = addPiece(502, PieceStage.READY);
        var earlyRecord = LocalDateTime.of(2026, 1, 5, 10, 0);
        var lateRecord = LocalDateTime.of(2026, 1, 6, 10, 0);
        history(piece, PieceStage.CASTING, lateRecord);
        history(piece, PieceStage.HEAT_TREATMENT, earlyRecord);
        history(piece, PieceStage.RADIOGRAPHY, LocalDateTime.of(2026, 1, 7, 10, 0));
        for (PieceStage stage : List.of(PieceStage.CASTING, PieceStage.HEAT_TREATMENT)) {
            var date = new PieceStageProcessDate();
            date.setPiece(piece);
            date.setStage(stage);
            date.setProcessDate(stage == PieceStage.CASTING ? LocalDate.of(2026, 1, 2) : LocalDate.of(2026, 1, 3));
            stageDates.saveAndFlush(date);
        }
        var result = pieceService.getStageHistory(piece.getId());
        assertEquals(List.of(PieceStage.RADIOGRAPHY, PieceStage.CASTING, PieceStage.HEAT_TREATMENT),
                result.stream().map(r -> r.getStage()).toList());
        assertEquals(LocalDate.of(2026, 1, 2), result.get(1).getProcessDate());
        assertEquals(LocalDate.of(2026, 1, 3), result.get(2).getProcessDate());
        assertEquals(LocalDateTime.of(2026, 1, 7, 10, 0), result.get(0).getChangedAt());
        assertEquals(lateRecord, result.get(1).getChangedAt());
        assertEquals(earlyRecord, result.get(2).getChangedAt());
        var castingDate = stageDates.findByPieceIdAndStage(piece.getId(), PieceStage.CASTING).orElseThrow();
        castingDate.setProcessDate(LocalDate.of(2026, 1, 3));
        stageDates.saveAndFlush(castingDate);
        result = pieceService.getStageHistory(piece.getId());
        assertEquals(PieceStage.CASTING, result.get(1).getStage());
        assertEquals(result.stream().map(r -> r.getId()).toList(), pieceService.getStageHistory(piece.getId()).stream().map(r -> r.getId()).toList());
    }

    @Test
    void substageHistoryUsesExistingDatesAndLiveDateOverridesTheStoredCurrentLocation() {
        Piece piece = addPiece(503, PieceStage.RADIOGRAPHY);
        piece.setRadiographySubStage(RadiographySubStage.VT);
        piece.setProcessDate(LocalDate.of(2026, 1, 8));
        pieceRepository.saveAndFlush(piece);
        for (String sub : List.of("SHOT", "INTERPRETATION", "VT")) {
            var h = new PieceSubStageHistory();
            h.setPiece(piece); h.setKind(SubStageKind.RADIOGRAPHY); h.setSubStage(sub);
            subHistory.saveAndFlush(h);
            // Windows clock resolution can give consecutive saves the same timestamp.
            // This test asserts recording-time order, so make that fixture order explicit.
            em.createQuery("update PieceSubStageHistory h set h.changedAt = :at where h.id = :id")
                    .setParameter("at", LocalDateTime.of(2026, 1, 1, 10, 0)
                            .plusSeconds(sub.equals("SHOT") ? 0 : sub.equals("INTERPRETATION") ? 1 : 2))
                    .setParameter("id", h.getId()).executeUpdate();
            em.refresh(h);
            var d = new PieceSubStageProcessDate();
            d.setPiece(piece); d.setKind(SubStageKind.RADIOGRAPHY); d.setSubStage(sub);
            d.setProcessDate(LocalDate.of(2026, 1, sub.equals("SHOT") ? 5 : sub.equals("INTERPRETATION") ? 3 : 1));
            subDates.saveAndFlush(d);
        }
        var result = pieceService.getSubStageHistory(piece.getId());
        assertEquals(List.of("VT", "INTERPRETATION", "SHOT"), result.stream().map(r -> r.getSubStage()).toList());
        assertEquals(LocalDate.of(2026, 1, 8), result.get(0).getProcessDate());
        assertEquals(LocalDate.of(2026, 1, 1), subDates.findByPiece_IdAndKindAndSubStage(piece.getId(), SubStageKind.RADIOGRAPHY, "VT").orElseThrow().getProcessDate());
    }

    @Test
    void repeatedAgingDurationsRemainVisibleBesideTheNewestNextTensileDecision() {
        Piece piece = tensilePiece();
        test(piece, 1, TensileTestStatus.REJECTED, TensileNextStage.AGING, "0.50");
        history(piece, PieceStage.HEAT_TREATMENT, LocalDateTime.of(2026, 1, 2, 11, 0));
        history(piece, PieceStage.TENSILE, LocalDateTime.of(2026, 1, 3, 10, 0));
        test(piece, 2, TensileTestStatus.REJECTED, TensileNextStage.AGING, "1.00");
        history(piece, PieceStage.HEAT_TREATMENT, LocalDateTime.of(2026, 1, 4, 11, 0));
        history(piece, PieceStage.TENSILE, LocalDateTime.of(2026, 1, 5, 10, 0));
        test(piece, 3, TensileTestStatus.REJECTED, TensileNextStage.NEXT_TENSILE, null);

        var info = tracked(piece).getTensileTrackingInfo();
        assertNotNull(info);
        assertEquals(TensileNextStage.NEXT_TENSILE, info.nextStage());
        assertNull(info.agingDurationMinutes());
        assertEquals(List.of(new BigDecimal("30.00"), new BigDecimal("60.00")), info.agingDurationsMinutes());
    }

    @Test
    void automaticTensileApprovalStillMovesThePieceAndSuppressesContext() {
        Piece piece = tensilePiece();
        definition.setMinUts(new BigDecimal("250"));
        definitionRepository.saveAndFlush(definition);
        var request = new com.factory.productmanager.model.dto.CreateTensileTestRequest();
        var sample = new com.factory.productmanager.model.dto.TensileSampleDto();
        sample.setUts(new BigDecimal("250"));
        request.setSamples(List.of(sample));
        var response = tensileService.createTest(piece.getId(), request, admin.getUsername());
        assertEquals(TensileTestStatus.APPROVED, response.getStatus());
        assertEquals(PieceStage.SHOT_BLASTING, pieceRepository.findById(piece.getId()).orElseThrow().getCurrentStage());
        assertNull(tracked(piece).getTensileTrackingInfo());
    }

    @Test
    void recordedAgingDecisionSurvivesTheRealHeatReturnAndIsSerializedInMinutes() throws Exception {
        Piece piece = tensilePiece();
        var create = new com.factory.productmanager.model.dto.CreateTensileTestRequest();
        var sample = new com.factory.productmanager.model.dto.TensileSampleDto();
        sample.setUts(new BigDecimal("200"));
        create.setSamples(List.of(sample));
        var test = tensileService.createTest(piece.getId(), create, admin.getUsername());
        var disposition = new com.factory.productmanager.model.dto.SaveTensileDispositionRequest();
        disposition.setDispositionDate(LocalDate.now());
        disposition.setNextStage(TensileNextStage.AGING);
        disposition.setInspectionResult(TensileInspectionResult.REJECTED);
        disposition.setDurationHours(new BigDecimal("1.25"));
        tensileService.saveDisposition(test.getId(), disposition, admin.getUsername());
        assertEquals(PieceStage.HEAT_TREATMENT, piece.getCurrentStage());
        var move = new com.factory.productmanager.model.dto.UpdateStageRequest();
        move.setNewStage(PieceStage.TENSILE);
        pieceService.updateStage(piece.getId(), move, admin.getUsername());
        call(get("/api/pieces/tracking").param("q", "501").param("projectCode", code), worker)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].tensileTrackingInfo.nextStage").value("AGING"))
                .andExpect(jsonPath("$.items[0].tensileTrackingInfo.agingDurationMinutes").value(75));
    }

    @Test
    void exactNumericQueryStillIntersectsRadiographyAndHeatSubstageFilters() throws Exception {
        Piece piece = pieceRepository.findByPieceNumberAndArchivedAtIsNull(9).get(0);
        piece.setCurrentStage(PieceStage.RADIOGRAPHY);
        piece.setRadiographySubStage(RadiographySubStage.SHOT);
        pieceRepository.saveAndFlush(piece);
        call(get("/api/pieces/tracking").param("q", "9").param("projectCode", code)
                        .param("stage", "RADIOGRAPHY").param("radiographySubStage", "VT"), worker)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        call(get("/api/pieces/tracking").param("q", "9").param("projectCode", code)
                        .param("stage", "RADIOGRAPHY").param("radiographySubStage", "SHOT"), worker)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        piece.setCurrentStage(PieceStage.HEAT_TREATMENT);
        piece.setHeatTreatmentSubStage(HeatTreatmentSubStage.STAGE_3);
        pieceRepository.saveAndFlush(piece);
        call(get("/api/pieces/tracking").param("q", "9").param("projectCode", code)
                        .param("stage", "HEAT_TREATMENT").param("heatTreatmentSubStage", "STAGE_3"), worker)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }
}
