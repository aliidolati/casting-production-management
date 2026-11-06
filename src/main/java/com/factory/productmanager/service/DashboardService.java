package com.factory.productmanager.service;

import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.BottleneckItem;
import com.factory.productmanager.model.dto.DashboardAlertsResponse;
import com.factory.productmanager.model.dto.DashboardOverviewResponse;
import com.factory.productmanager.model.dto.DashboardStatsResponse;
import com.factory.productmanager.model.dto.NeedsWeldingAlertItem;
import com.factory.productmanager.model.dto.RadiographyDefectReportPieceItem;
import com.factory.productmanager.model.dto.RadiographyDefectReportResponse;
import com.factory.productmanager.model.dto.RadiographyDefectReportRow;
import com.factory.productmanager.model.dto.RecentStageMoveItem;
import com.factory.productmanager.model.dto.StageOccupancyReportResponse;
import com.factory.productmanager.model.dto.StageOccupancyRow;
import com.factory.productmanager.model.dto.StuckPieceAlertItem;
import com.factory.productmanager.model.dto.SubStageOverdueAlertItem;
import com.factory.productmanager.model.entity.DefectStatus;
import com.factory.productmanager.model.entity.DefectTypes;
import com.factory.productmanager.model.entity.HeatTreatmentSubStage;
import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.PieceDefinition;
import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.PieceStageHistory;
import com.factory.productmanager.model.entity.Project;
import com.factory.productmanager.model.entity.RadiographyDefect;
import com.factory.productmanager.model.entity.RadiographySubStage;
import com.factory.productmanager.model.entity.SubStageKind;
import com.factory.productmanager.model.entity.PieceSubStageHistory;
import com.factory.productmanager.model.entity.PieceSubStageProcessDate;
import com.factory.productmanager.repository.PieceSubStageProcessDateRepository;
import com.factory.productmanager.repository.PieceDefinitionRepository;
import com.factory.productmanager.repository.PieceRepository;
import com.factory.productmanager.repository.PieceStageHistoryRepository;
import com.factory.productmanager.repository.PieceSubStageHistoryRepository;
import com.factory.productmanager.repository.PieceWeldingDispatchRepository;
import com.factory.productmanager.repository.ProjectRepository;
import com.factory.productmanager.repository.PieceStageProcessDateRepository;
import com.factory.productmanager.repository.RadiographyDefectRepository;
import com.factory.productmanager.repository.TensileTestRepository;
import com.factory.productmanager.model.dto.TensileQualityPointResponse;
import com.factory.productmanager.model.dto.TensileQualityReportResponse;
import com.factory.productmanager.model.dto.TensileQualityStatsResponse;
import com.factory.productmanager.model.entity.PieceWeldingDispatch;
import com.factory.productmanager.model.entity.TensileTestSample;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final ProjectRepository projectRepository;
    private final PieceDefinitionRepository pieceDefinitionRepository;
    private final PieceRepository pieceRepository;
    private final PieceStageHistoryRepository pieceStageHistoryRepository;
    private final PieceSubStageHistoryRepository pieceSubStageHistoryRepository;
    private final PieceSubStageProcessDateRepository pieceSubStageProcessDateRepository;
    private final PieceWeldingDispatchRepository pieceWeldingDispatchRepository;
    private final RadiographyDefectRepository radiographyDefectRepository;
    private final PieceStageProcessDateRepository pieceStageProcessDateRepository;
    private final TensileTestRepository tensileTestRepository;

    private static final EnumSet<PieceStage> STAGES_EXCLUDED_FROM_STUCK_ALERT =
            EnumSet.of(PieceStage.NOT_STARTED, PieceStage.READY, PieceStage.DELIVERED, PieceStage.SCRAPPED);

    private static final EnumSet<PieceStage> STAGES_EXCLUDED_FROM_WIP =
            EnumSet.of(PieceStage.NOT_STARTED, PieceStage.DELIVERED, PieceStage.SCRAPPED);

    private static final int RECENT_MOVES_LIMIT = 20;

    /** سقف تعداد آیتم هر لیست هشدار در پاسخ overview (شمارندهٔ واقعی جدا در فیلدهای *Total می‌آید) */
    private static final int OVERVIEW_ALERT_LIST_LIMIT = 50;

    private static final LocalDateTime EPOCH_START = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(9000, 1, 1, 0, 0);

    public DashboardStatsResponse getStats() {
        return toStats(buildOverview(null, null, null, 7, 3, Integer.MAX_VALUE));
    }

    public DashboardAlertsResponse getAlerts(int stuckThresholdDays) {
        DashboardOverviewResponse overview = buildOverview(null, null, null, stuckThresholdDays, 3, Integer.MAX_VALUE);
        return new DashboardAlertsResponse(
                overview.getNeedsWelding(),
                overview.getStuckPieces(),
                overview.getStuckThresholdDays()
        );
    }

    public DashboardOverviewResponse getOverview(
            UUID projectId,
            LocalDate dateFrom,
            LocalDate dateTo,
            int stuckThresholdDays,
            int subStageThresholdDays
    ) {
        return buildOverview(
                projectId, dateFrom, dateTo, stuckThresholdDays, subStageThresholdDays, OVERVIEW_ALERT_LIST_LIMIT
        );
    }

    private static <T> List<T> limitList(List<T> list, int limit) {
        if (list == null || list.size() <= limit) {
            return list;
        }
        return List.copyOf(list.subList(0, limit));
    }

    private DashboardOverviewResponse buildOverview(
            UUID projectId,
            LocalDate dateFrom,
            LocalDate dateTo,
            int stuckThresholdDays,
            int subStageThresholdDays,
            int alertListLimit
    ) {
        int stuckThreshold = Math.max(stuckThresholdDays, 1);
        int subStageThreshold = Math.max(subStageThresholdDays, 1);

        LocalDateTime periodFrom = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime periodToExclusive = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;

        LocalDateTime now = LocalDateTime.now();

        Map<PieceStage, Long> stageCounts = loadStageCounts(projectId);
        long totalPieces = projectId == null
                ? pieceRepository.countByArchivedAtIsNull()
                : pieceRepository.countByArchivedAtIsNullAndPieceDefinition_Project_Id(projectId);
        long totalDefinitions = projectId == null
                ? pieceDefinitionRepository.count()
                : pieceDefinitionRepository.countByProjectId(projectId);
        long totalProjects = projectId == null ? projectRepository.count() : 1L;

        long wipCount = stageCounts.entrySet().stream()
                .filter(e -> !STAGES_EXCLUDED_FROM_WIP.contains(e.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        double wipPercent = totalPieces == 0 ? 0.0 : round1(100.0 * wipCount / totalPieces);

        LocalDateTime metricFrom = periodFrom != null ? periodFrom : EPOCH_START;
        LocalDateTime metricTo = periodToExclusive != null ? periodToExclusive : now.plusSeconds(1);
        long deliveredInPeriod = countStageEntries(PieceStage.DELIVERED, metricFrom, metricTo, projectId);
        long readyInPeriod = countStageEntries(PieceStage.READY, metricFrom, metricTo, projectId);
        long shotBlastingInPeriod = countDistinctStageEntries(PieceStage.SHOT_BLASTING, metricFrom, metricTo, projectId);
        long heatTreatmentStage1InPeriod = countDistinctSubStageEntries(
                SubStageKind.HEAT_TREATMENT,
                HeatTreatmentSubStage.STAGE_1.name(),
                metricFrom,
                metricTo,
                projectId
        );
        long scrappedCount = stageCounts.getOrDefault(PieceStage.SCRAPPED, 0L);
        long finishedInPeriod = deliveredInPeriod + scrappedCount;
        // نرخ اسقاط شامل بایگانی‌هاست؛ قطعات شروع‌نشده فقط از مخرج حذف می‌شوند.
        long allScrappedCount = 0;
        long startedPiecesCount = 0;
        for (Object[] row : pieceRepository.countGroupByCurrentStageIncludingArchived(projectId)) {
            PieceStage stage = (PieceStage) row[0];
            long count = (Long) row[1];
            if (stage != PieceStage.NOT_STARTED) startedPiecesCount += count;
            if (stage == PieceStage.SCRAPPED) allScrappedCount = count;
        }
        Double scrapRatePercent = startedPiecesCount == 0
                ? null
                : round1(100.0 * allScrappedCount / startedPiecesCount);

        Double avgCycleTimeDays = computeAvgCycleTimeDays(metricFrom, metricTo, projectId);

        // یک بار همه‌ی قطعات فعال (همراه تعریف و پروژه) و یک بار «آخرین تغییر مرحله‌ی هر قطعه» لود می‌شود
        // و بین محاسبه‌ی گلوگاه، هشدار گیرکرده و هشدارهای زیرمرحله مشترک است (قبلاً هر کدام جدا کوئری می‌زدند).
        List<Piece> activePieces = projectId == null
                ? pieceRepository.findAllWithDefinitionAndProject()
                : pieceRepository.findAllWithDefinitionAndProjectByProjectId(projectId);
        Map<UUID, LocalDateTime> lastStageChangeByPiece = loadLatestStageChangeByPiece();

        List<Piece> wipPieces = activePieces.stream()
                .filter(p -> p.getCurrentStage() != null && !STAGES_EXCLUDED_FROM_WIP.contains(p.getCurrentStage()))
                .toList();

        BottleneckItem bottleneckStage = findBottleneckStage(wipPieces);
        BottleneckItem bottleneckProject = projectId == null ? findBottleneckProject(wipPieces) : null;

        List<NeedsWeldingAlertItem> needsWelding = findNeedsWeldingAlerts(projectId);
        List<StuckPieceAlertItem> stuckPieces = findStuckPieceAlerts(stuckThreshold, activePieces, lastStageChangeByPiece);
        List<SubStageOverdueAlertItem> awaitingInterpretation = findSubStageOverdueAlerts(
                SubStageKind.RADIOGRAPHY,
                RadiographySubStage.INTERPRETATION.name(),
                PieceStage.RADIOGRAPHY,
                subStageThreshold,
                activePieces,
                lastStageChangeByPiece
        );
        List<SubStageOverdueAlertItem> awaitingOperations = findSubStageOverdueAlerts(
                SubStageKind.HEAT_TREATMENT,
                HeatTreatmentSubStage.AWAITING_OPERATIONS.name(),
                PieceStage.HEAT_TREATMENT,
                subStageThreshold,
                activePieces,
                lastStageChangeByPiece
        );

        List<RecentStageMoveItem> recentMoves = findRecentStageMoves(projectId, periodFrom, periodToExclusive);

        return new DashboardOverviewResponse(
                projectId,
                dateFrom,
                dateTo,
                totalProjects,
                totalDefinitions,
                totalPieces,
                stageCounts,
                wipCount,
                wipPercent,
                deliveredInPeriod,
                readyInPeriod,
                shotBlastingInPeriod,
                heatTreatmentStage1InPeriod,
                avgCycleTimeDays,
                scrapRatePercent,
                scrappedCount,
                finishedInPeriod,
                bottleneckProject,
                bottleneckStage,
                limitList(needsWelding, alertListLimit),
                limitList(stuckPieces, alertListLimit),
                limitList(awaitingInterpretation, alertListLimit),
                limitList(awaitingOperations, alertListLimit),
                stuckThreshold,
                subStageThreshold,
                recentMoves,
                needsWelding.size(),
                stuckPieces.size(),
                awaitingInterpretation.size(),
                awaitingOperations.size()
        );
    }

    private Map<UUID, LocalDateTime> loadLatestStageChangeByPiece() {
        Map<UUID, LocalDateTime> result = new HashMap<>();
        for (Object[] row : pieceStageHistoryRepository.findLatestChangedAtGroupedByPieceId()) {
            result.put((UUID) row[0], (LocalDateTime) row[1]);
        }
        return result;
    }

    private DashboardStatsResponse toStats(DashboardOverviewResponse overview) {
        return new DashboardStatsResponse(
                overview.getTotalProjects(),
                overview.getTotalDefinitions(),
                overview.getTotalPieces(),
                overview.getStageCounts()
        );
    }

    private Map<PieceStage, Long> loadStageCounts(UUID projectId) {
        Map<PieceStage, Long> stageCounts = new EnumMap<>(PieceStage.class);
        for (PieceStage stage : PieceStage.values()) {
            stageCounts.put(stage, 0L);
        }
        List<Object[]> rows = projectId == null
                ? pieceRepository.countGroupByCurrentStage()
                : pieceRepository.countGroupByCurrentStageForProject(projectId);
        for (Object[] row : rows) {
            stageCounts.put((PieceStage) row[0], (Long) row[1]);
        }
        return stageCounts;
    }

    private long countStageEntries(PieceStage stage, LocalDateTime from, LocalDateTime to, UUID projectId) {
        return projectId == null
                ? pieceStageHistoryRepository.countByStageBetween(stage, from, to)
                : pieceStageHistoryRepository.countByStageBetweenAndProject(stage, from, to, projectId);
    }

    private long countDistinctStageEntries(PieceStage stage, LocalDateTime from, LocalDateTime to, UUID projectId) {
        return projectId == null
                ? pieceStageHistoryRepository.countDistinctPiecesByStageBetween(stage, from, to)
                : pieceStageHistoryRepository.countDistinctPiecesByStageBetweenAndProject(stage, from, to, projectId);
    }

    private long countDistinctSubStageEntries(
            SubStageKind kind,
            String subStage,
            LocalDateTime from,
            LocalDateTime to,
            UUID projectId
    ) {
        return projectId == null
                ? pieceSubStageHistoryRepository.countDistinctPiecesBySubStageBetween(kind, subStage, from, to)
                : pieceSubStageHistoryRepository.countDistinctPiecesBySubStageBetweenAndProject(
                        kind, subStage, from, to, projectId
                );
    }

    private Double computeAvgCycleTimeDays(LocalDateTime from, LocalDateTime to, UUID projectId) {
        // فقط (شناسه قطعه، زمان) ورودهای READY؛ بدون لود Entity و JOIN FETCH
        List<Object[]> readyEntries = projectId == null
                ? pieceStageHistoryRepository.findPieceIdAndChangedAtByStageBetween(PieceStage.READY, from, to)
                : pieceStageHistoryRepository.findPieceIdAndChangedAtByStageBetweenAndProject(PieceStage.READY, from, to, projectId);

        if (readyEntries.isEmpty()) {
            return null;
        }

        // اولین حرکت هر قطعه با یک GROUP BY در دیتابیس (قبلاً کل جدول تاریخچه با findAll() لود می‌شد)
        Map<UUID, LocalDateTime> firstMoveByPiece = new HashMap<>();
        for (Object[] row : pieceStageHistoryRepository.findEarliestChangedAtGroupedByPieceId()) {
            firstMoveByPiece.put((UUID) row[0], (LocalDateTime) row[1]);
        }

        List<Double> days = new ArrayList<>();
        for (Object[] ready : readyEntries) {
            LocalDateTime readyAt = (LocalDateTime) ready[1];
            LocalDateTime first = firstMoveByPiece.get((UUID) ready[0]);
            if (first == null || !first.isBefore(readyAt)) {
                continue;
            }
            days.add(Duration.between(first, readyAt).toMinutes() / (60.0 * 24.0));
        }
        if (days.isEmpty()) {
            return null;
        }
        double avg = days.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return round1(avg);
    }

    private BottleneckItem findBottleneckStage(List<Piece> wipPieces) {
        Map<PieceStage, Long> counts = wipPieces.stream()
                .filter(p -> p.getCurrentStage() != PieceStage.READY)
                .collect(Collectors.groupingBy(Piece::getCurrentStage, Collectors.counting()));
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> new BottleneckItem(null, null, e.getKey(), e.getValue()))
                .orElse(null);
    }

    private BottleneckItem findBottleneckProject(List<Piece> wipPieces) {
        Map<UUID, List<Piece>> byProject = wipPieces.stream()
                .collect(Collectors.groupingBy(p -> p.getPieceDefinition().getProject().getId()));
        return byProject.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().size()))
                .map(e -> {
                    Piece sample = e.getValue().get(0);
                    return new BottleneckItem(
                            e.getKey(),
                            sample.getPieceDefinition().getProject().getCode(),
                            null,
                            e.getValue().size()
                    );
                })
                .orElse(null);
    }

    private List<NeedsWeldingAlertItem> findNeedsWeldingAlerts(UUID projectId) {
        List<Piece> pieces = projectId == null
                ? pieceRepository.findAllWithDefinitionAndProjectByCurrentStage(PieceStage.WELDING)
                : pieceRepository.findAllWithDefinitionAndProjectByCurrentStageAndProject(PieceStage.WELDING, projectId);

        if (pieces.isEmpty()) {
            return List.of();
        }

        List<UUID> pieceIds = pieces.stream().map(Piece::getId).toList();
        Map<UUID, PieceWeldingDispatch> openByPiece = new HashMap<>();
        for (PieceWeldingDispatch dispatch : pieceWeldingDispatchRepository.findByPiece_IdInAndCompletedAtIsNull(pieceIds)) {
            openByPiece.putIfAbsent(dispatch.getPiece().getId(), dispatch);
        }

        return pieces.stream()
                .map(piece -> {
                    PieceWeldingDispatch dispatch = openByPiece.get(piece.getId());
                    return new NeedsWeldingAlertItem(
                            piece.getId(),
                            piece.getPieceDefinition().getProject().getCode(),
                            piece.getPieceDefinition().getPieceName(),
                            piece.getPieceNumber(),
                            piece.getCurrentStage(),
                            dispatch != null ? DefectTypes.displayLabel(dispatch.getDefectType(), dispatch.getDefectTypeCustom()) : null,
                            dispatch != null ? dispatch.getNotes() : null,
                            dispatch != null ? dispatch.getSentBy() : null,
                            dispatch != null ? dispatch.getSentAt() : null,
                            List.of()
                    );
                })
                .sorted(Comparator.comparing(
                        NeedsWeldingAlertItem::getSentAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    public List<NeedsWeldingAlertItem> getWeldingStationReport(UUID projectId) {
        return findNeedsWeldingAlerts(projectId);
    }

    public TensileQualityReportResponse getTensileQualityReport(
            UUID projectId,
            UUID pieceDefinitionId,
            List<String> sampleLabels,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("تاریخ پایان نمی‌تواند قبل از تاریخ شروع باشد");
        }
        List<String> labels = sampleLabels == null
                ? List.of()
                : sampleLabels.stream().filter(label -> label != null && !label.isBlank()).map(String::trim).distinct().toList();
        boolean noLabelFilter = labels.isEmpty();
        LocalDateTime from = dateFrom == null ? null : dateFrom.atStartOfDay();
        LocalDateTime toExclusive = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay();

        List<TensileQualityPointResponse> points = new ArrayList<>();
        int green = 0;
        int yellow = 0;
        int red = 0;
        int undefined = 0;
        int unplottable = 0;
        BigDecimal sumQ = BigDecimal.ZERO;
        BigDecimal sumYs = BigDecimal.ZERO;
        BigDecimal sumEl = BigDecimal.ZERO;
        int definedQCount = 0;

        for (TensileTestSample sample : tensileTestRepository.findSamplesForQualityReport(
                projectId, pieceDefinitionId, noLabelFilter, noLabelFilter ? List.of("") : labels, from, toExclusive
        )) {
            if (sample.getEl() == null || sample.getYs() == null) {
                unplottable++;
                continue;
            }
            TensileQualityIndex.Evaluation evaluation = TensileQualityIndex.evaluate(sample.getEl(), sample.getYs());
            switch (evaluation.band()) {
                case GREEN -> {
                    green++;
                    definedQCount++;
                    sumQ = sumQ.add(evaluation.q());
                }
                case YELLOW -> {
                    yellow++;
                    definedQCount++;
                    sumQ = sumQ.add(evaluation.q());
                }
                case RED -> {
                    red++;
                    definedQCount++;
                    sumQ = sumQ.add(evaluation.q());
                }
                case UNDEFINED -> undefined++;
            }
            sumYs = sumYs.add(sample.getYs());
            sumEl = sumEl.add(sample.getEl());

            var piece = sample.getTensileTest().getPiece();
            var definition = piece.getPieceDefinition();
            points.add(new TensileQualityPointResponse(
                    sample.getId(),
                    sample.getSampleLabel(),
                    definition.getProject().getCode(),
                    definition.getPieceName(),
                    piece.getPieceNumber(),
                    sample.getEl(),
                    sample.getYs(),
                    evaluation.q(),
                    evaluation.band().name()
            ));
        }

        int plotted = points.size();
        TensileQualityStatsResponse stats = new TensileQualityStatsResponse(
                plotted,
                green,
                yellow,
                red,
                undefined,
                unplottable,
                average(sumQ, definedQCount, 4),
                average(sumYs, plotted, 2),
                average(sumEl, plotted, 2)
        );
        return new TensileQualityReportResponse(points, stats);
    }

    private static BigDecimal average(BigDecimal sum, int count, int scale) {
        if (count <= 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), scale, RoundingMode.HALF_UP);
    }

    private List<StuckPieceAlertItem> findStuckPieceAlerts(
            int thresholdDays,
            List<Piece> activePieces,
            Map<UUID, LocalDateTime> lastChangeByPieceId
    ) {
        LocalDateTime now = LocalDateTime.now();

        return activePieces.stream()
                .filter(p -> p.getCurrentStage() != null && !STAGES_EXCLUDED_FROM_STUCK_ALERT.contains(p.getCurrentStage()))
                .map(p -> {
                    LocalDateTime reference = lastChangeByPieceId.getOrDefault(p.getId(), p.getCreatedAt());
                    long daysInStage = Duration.between(reference, now).toDays();
                    return new StuckPieceAlertItem(
                            p.getId(),
                            p.getPieceDefinition().getProject().getCode(),
                            p.getPieceDefinition().getPieceName(),
                            p.getPieceNumber(),
                            p.getCurrentStage(),
                            daysInStage
                    );
                })
                .filter(item -> item.getDaysInStage() >= thresholdDays)
                .sorted(Comparator.comparingLong(StuckPieceAlertItem::getDaysInStage).reversed())
                .toList();
    }

    private List<SubStageOverdueAlertItem> findSubStageOverdueAlerts(
            SubStageKind kind,
            String subStage,
            PieceStage requiredStage,
            int thresholdDays,
            List<Piece> pieces,
            Map<UUID, LocalDateTime> stageEntered
    ) {
        Map<UUID, LocalDateTime> enteredAt = new HashMap<>();
        for (Object[] row : pieceSubStageHistoryRepository.findLatestChangedAtByKindAndSubStage(kind, subStage)) {
            enteredAt.put((UUID) row[0], (LocalDateTime) row[1]);
        }

        LocalDateTime now = LocalDateTime.now();

        return pieces.stream()
                .filter(p -> p.getCurrentStage() == requiredStage)
                .filter(p -> matchesCurrentSubStage(p, kind, subStage))
                .map(p -> {
                    LocalDateTime reference = enteredAt.get(p.getId());
                    if (reference == null) {
                        reference = stageEntered.getOrDefault(p.getId(), p.getCreatedAt());
                    }
                    long days = Duration.between(reference, now).toDays();
                    return new SubStageOverdueAlertItem(
                            p.getId(),
                            p.getPieceDefinition().getProject().getCode(),
                            p.getPieceDefinition().getPieceName(),
                            p.getPieceNumber(),
                            p.getCurrentStage(),
                            kind,
                            subStage,
                            days
                    );
                })
                .filter(item -> item.getDaysInSubStage() >= thresholdDays)
                .sorted(Comparator.comparingLong(SubStageOverdueAlertItem::getDaysInSubStage).reversed())
                .toList();
    }

    private static boolean matchesCurrentSubStage(Piece piece, SubStageKind kind, String subStage) {
        if (kind == SubStageKind.RADIOGRAPHY) {
            return piece.getRadiographySubStage() != null
                    && piece.getRadiographySubStage().name().equals(subStage);
        }
        if (kind == SubStageKind.HEAT_TREATMENT) {
            HeatTreatmentSubStage current = piece.getHeatTreatmentSubStage();
            if (current == null) {
                current = HeatTreatmentSubStage.AWAITING_OPERATIONS;
            }
            return current.name().equals(subStage);
        }
        return false;
    }

    private List<RecentStageMoveItem> findRecentStageMoves(
            UUID projectId,
            LocalDateTime periodFrom,
            LocalDateTime periodToExclusive
    ) {
        // فیلتر بازه و LIMIT در خود SQL انجام می‌شود (قبلاً کل تاریخچه لود و در حافظه فیلتر می‌شد)
        LocalDateTime from = periodFrom != null ? periodFrom : EPOCH_START;
        LocalDateTime to = periodToExclusive != null ? periodToExclusive : FAR_FUTURE;
        Pageable firstPage = PageRequest.of(0, RECENT_MOVES_LIMIT);
        List<PieceStageHistory> histories = projectId == null
                ? pieceStageHistoryRepository.findRecentWithPieceDetails(from, to, firstPage)
                : pieceStageHistoryRepository.findRecentWithPieceDetailsByProject(projectId, from, to, firstPage);

        return histories.stream()
                .map(h -> {
                    Piece p = h.getPiece();
                    return new RecentStageMoveItem(
                            h.getId(),
                            p.getId(),
                            p.getPieceDefinition().getProject().getCode(),
                            p.getPieceDefinition().getPieceName(),
                            p.getPieceNumber(),
                            h.getStage(),
                            h.getChangedBy(),
                            h.getChangedAt()
                    );
                })
                .toList();
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * مبنای تاریخ این گزارش «تاریخ انجام فرآیند» هر مرحله است.
     * اگر برای مرحله‌ای تاریخ دستی نباشد، یا آن تاریخ بعد از خروج قطعه از همان مرحله باشد،
     * تاریخ ورود به مرحله (یا تاریخ ثبت قطعه) استفاده می‌شود.
     */
    public StageOccupancyReportResponse getStageOccupancyReport(
            UUID projectId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        if (dateFrom == null || dateTo == null) {
            throw new IllegalArgumentException("انتخاب تاریخ شروع و پایان الزامی است");
        }
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("تاریخ پایان نمی‌تواند قبل از تاریخ شروع باشد");
        }

        String projectCode = null;
        if (projectId != null) {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("پروژه پیدا نشد"));
            projectCode = project.getCode();
        }

        LocalDateTime rangeFrom = dateFrom.atStartOfDay();
        LocalDateTime rangeToExclusive = dateTo.plusDays(1).atStartOfDay();

        List<Piece> pieces = projectId == null
                ? pieceRepository.findAllWithDefinitionAndProject()
                : pieceRepository.findAllWithDefinitionAndProjectByProjectId(projectId);

        List<UUID> pieceIds = pieces.stream().map(Piece::getId).toList();
        Map<UUID, List<PieceStageHistory>> historyByPiece = new HashMap<>();
        Map<UUID, Map<PieceStage, LocalDate>> processDatesByPiece = new HashMap<>();
        Map<UUID, List<PieceSubStageHistory>> subHistoryByPiece = new HashMap<>();
        Map<UUID, List<PieceSubStageProcessDate>> subDatesByPiece = new HashMap<>();
        if (!pieceIds.isEmpty()) {
            for (var history : pieceSubStageHistoryRepository.findByPiece_IdInOrderByChangedAtAsc(pieceIds)) {
                subHistoryByPiece.computeIfAbsent(history.getPiece().getId(), key -> new ArrayList<>()).add(history);
            }
            for (var date : pieceSubStageProcessDateRepository.findByPiece_IdIn(pieceIds)) {
                subDatesByPiece.computeIfAbsent(date.getPiece().getId(), key -> new ArrayList<>()).add(date);
            }
            for (PieceStageHistory h : pieceStageHistoryRepository.findByPiece_IdInOrderByChangedAtAsc(pieceIds)) {
                historyByPiece.computeIfAbsent(h.getPiece().getId(), k -> new ArrayList<>()).add(h);
            }
            for (var row : pieceStageProcessDateRepository.findByPiece_IdIn(pieceIds)) {
                processDatesByPiece
                        .computeIfAbsent(row.getPiece().getId(), k -> new EnumMap<>(PieceStage.class))
                        .put(row.getStage(), row.getProcessDate());
            }
        }

        List<StageOccupancyRow> rows = new ArrayList<>();
        for (Piece piece : pieces) {
            rows.addAll(occupancyRowsForPiece(
                    piece,
                    historyByPiece.getOrDefault(piece.getId(), List.of()),
                    processDatesByPiece.getOrDefault(piece.getId(), Map.of()),
                    rangeFrom,
                    rangeToExclusive
            ));
            rows.addAll(subStageOccupancyRowsForPiece(piece,
                    historyByPiece.getOrDefault(piece.getId(), List.of()),
                    subHistoryByPiece.getOrDefault(piece.getId(), List.of()),
                    subDatesByPiece.getOrDefault(piece.getId(), List.of()), rangeFrom, rangeToExclusive));
        }

        rows.sort(Comparator
                .comparing(StageOccupancyRow::getProjectCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(StageOccupancyRow::getRowNum, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(StageOccupancyRow::getPieceNumber, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(StageOccupancyRow::getEnteredAt, Comparator.nullsLast(LocalDateTime::compareTo)));

        return new StageOccupancyReportResponse(projectId, projectCode, dateFrom, dateTo, rows);
    }

    private List<StageOccupancyRow> occupancyRowsForPiece(
            Piece piece,
            List<PieceStageHistory> historyAsc,
            Map<PieceStage, LocalDate> processDates,
            LocalDateTime rangeFrom,
            LocalDateTime rangeToExclusive
    ) {
        List<PieceStageHistory> unique = new ArrayList<>();
        for (PieceStageHistory h : historyAsc) {
            if (unique.isEmpty() || unique.get(unique.size() - 1).getStage() != h.getStage()) {
                unique.add(h);
            }
        }

        LinkedHashSet<PieceStage> stages = new LinkedHashSet<>(processDates.keySet());
        unique.forEach(h -> stages.add(h.getStage()));
        if (piece.getCurrentStage() != null) {
            stages.add(piece.getCurrentStage());
        } else {
            stages.add(PieceStage.NOT_STARTED);
        }

        PieceDefinition definition = piece.getPieceDefinition();
        List<StageOccupancyRow> rows = new ArrayList<>();
        for (PieceStage stage : stages) {
            LocalDateTime enteredAt = resolveOccupancyEnteredAt(piece, unique, processDates, stage);
            if (enteredAt == null || enteredAt.isBefore(rangeFrom) || !enteredAt.isBefore(rangeToExclusive)) {
                continue;
            }
            boolean stillInStage = piece.getCurrentStage() == stage;
            rows.add(new StageOccupancyRow(
                    piece.getId(),
                    definition.getProject().getCode(),
                    definition.getRowNum(),
                    definition.getPieceName(),
                    piece.getPieceNumber(),
                    stage,
                    null,
                    null,
                    enteredAt,
                    stillInStage ? null : findStageLeftAt(unique, stage),
                    stillInStage
            ));
        }
        return rows;
    }

    private List<StageOccupancyRow> subStageOccupancyRowsForPiece(
            Piece piece, List<PieceStageHistory> stageHistory, List<PieceSubStageHistory> subHistory,
            List<PieceSubStageProcessDate> processDates, LocalDateTime rangeFrom, LocalDateTime rangeToExclusive
    ) {
        List<StageOccupancyRow> rows = new ArrayList<>();
        for (SubStageKind kind : SubStageKind.values()) {
            PieceStage stage = PieceStage.valueOf(kind.name());
            Map<String, LocalDateTime> enteredBySubStage = new java.util.LinkedHashMap<>();
            subHistory.stream().filter(history -> history.getKind() == kind)
                    .forEach(history -> enteredBySubStage.put(reportSubStage(kind, history.getSubStage()), history.getChangedAt()));
            Map<String, LocalDate> dates = new HashMap<>();
            processDates.stream().filter(date -> date.getKind() == kind).forEach(date -> {
                String subStage = reportSubStage(kind, date.getSubStage());
                dates.put(subStage, date.getProcessDate());
                enteredBySubStage.putIfAbsent(subStage, date.getProcessDate().atStartOfDay());
            });
            String current = switch (kind) {
                case RADIOGRAPHY -> piece.getRadiographySubStage() == null ? null : piece.getRadiographySubStage().name();
                case HEAT_TREATMENT -> piece.getHeatTreatmentSubStage() == null ? null : piece.getHeatTreatmentSubStage().name();
                case ROUGH_MACHINING -> piece.getRoughMachiningSubStage() == null ? null : piece.getRoughMachiningSubStage().name();
            };
            if (piece.getCurrentStage() == stage && current != null) {
                LocalDateTime entered = stageHistory.stream().filter(history -> history.getStage() == stage)
                        .map(PieceStageHistory::getChangedAt).max(LocalDateTime::compareTo).orElse(piece.getCreatedAt());
                enteredBySubStage.putIfAbsent(current, entered);
            }
            for (var entry : enteredBySubStage.entrySet()) {
                String subStage = entry.getKey();
                LocalDateTime historyEntered = entry.getValue();
                boolean stillHere = piece.getCurrentStage() == stage && subStage.equals(current);
                LocalDateTime leftAt = stillHere ? null : java.util.stream.Stream.concat(
                                subHistory.stream().filter(history -> history.getKind() == kind
                                                && !reportSubStage(kind, history.getSubStage()).equals(subStage))
                                        .map(PieceSubStageHistory::getChangedAt),
                                stageHistory.stream().filter(history -> history.getStage() != stage)
                                        .map(PieceStageHistory::getChangedAt))
                        .filter(changedAt -> changedAt.isAfter(historyEntered))
                        .min(LocalDateTime::compareTo).orElse(null);
                LocalDate stored = dates.get(subStage);
                LocalDateTime enteredAt = historyEntered;
                if (stored != null && (leftAt == null || !stored.atStartOfDay().isAfter(leftAt))) {
                    enteredAt = stored.atStartOfDay();
                } else if (stillHere && piece.getProcessDate() != null) {
                    enteredAt = piece.getProcessDate().atStartOfDay();
                }
                if (enteredAt.isBefore(rangeFrom) || !enteredAt.isBefore(rangeToExclusive)) continue;
                PieceDefinition definition = piece.getPieceDefinition();
                rows.add(new StageOccupancyRow(piece.getId(), definition.getProject().getCode(), definition.getRowNum(),
                        definition.getPieceName(), piece.getPieceNumber(), stage, kind, subStage,
                        enteredAt, leftAt, stillHere));
            }
        }
        return rows;
    }

    private static String reportSubStage(SubStageKind kind, String subStage) {
        return kind == SubStageKind.RADIOGRAPHY ? RadiographySubStage.fromStored(subStage).name() : subStage;
    }

    private static LocalDateTime resolveOccupancyEnteredAt(
            Piece piece,
            List<PieceStageHistory> historyAsc,
            Map<PieceStage, LocalDate> processDates,
            PieceStage stage
    ) {
        LocalDateTime historyEntered = null;
        for (int i = historyAsc.size() - 1; i >= 0; i--) {
            if (historyAsc.get(i).getStage() == stage) {
                historyEntered = historyAsc.get(i).getChangedAt();
                break;
            }
        }
        LocalDateTime leftAt = findStageLeftAt(historyAsc, stage);

        LocalDate stored = processDates.get(stage);
        if (stored != null) {
            LocalDateTime processAt = stored.atStartOfDay();
            // تاریخ انجام فرآیند بعد از خروج، نمی‌تواند تاریخ همان حضور باشد.
            if (leftAt == null || !processAt.isAfter(leftAt)) {
                return processAt;
            }
        }
        if (piece.getCurrentStage() == stage && piece.getProcessDate() != null) {
            return piece.getProcessDate().atStartOfDay();
        }
        if (historyEntered != null) {
            return historyEntered;
        }
        if (piece.getCurrentStage() == stage) {
            return piece.getCreatedAt();
        }
        return null;
    }

    private static LocalDateTime findStageLeftAt(List<PieceStageHistory> historyAsc, PieceStage stage) {
        Integer lastIndex = null;
        for (int i = 0; i < historyAsc.size(); i++) {
            if (historyAsc.get(i).getStage() == stage) {
                lastIndex = i;
            }
        }
        if (lastIndex == null || lastIndex + 1 >= historyAsc.size()) {
            return null;
        }
        return historyAsc.get(lastIndex + 1).getChangedAt();
    }

    /**
     * گزارش عیوب رادیوگرافی — فقط برای این گزارش:
     * بدون تاریخ: قطعاتی که «شروع نشده» نیستند.
     * با تاریخ: قطعاتی که در آن بازه وارد ذوب‌ریزی (CASTING) شده‌اند.
     * فیلتر تاریخ آمار کلی داشبورد از این منطق جداست و اینجا تغییر نمی‌کند.
     */
    public RadiographyDefectReportResponse getRadiographyDefectReport(
            UUID projectId,
            List<UUID> pieceDefinitionIds,
            List<String> defectTypes,
            List<DefectStatus> statuses,
            List<Boolean> weldingConfirmedFilters,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("پروژه پیدا نشد"));

        Set<UUID> eligiblePieceIds = resolveEligiblePieceIdsForDefectReport(
                projectId,
                dateFrom,
                dateTo
        );
        if (pieceDefinitionIds != null && !pieceDefinitionIds.isEmpty()) {
            Set<UUID> definitionPieceIds = new HashSet<>(
                    pieceRepository.findIdsByProjectAndDefinitionIds(projectId, pieceDefinitionIds)
            );
            eligiblePieceIds.retainAll(definitionPieceIds);
        }

        List<RadiographyDefect> defects = radiographyDefectRepository.findAllForProjectReport(projectId);

        Set<String> defectTypeFilter = normalizeDefectTypeFilter(defectTypes);
        Set<DefectStatus> statusFilter = normalizeStatusFilter(statuses);
        Set<Boolean> weldingFilter = normalizeWeldingConfirmedFilter(weldingConfirmedFilters);

        defects = defects.stream()
                .filter(d -> eligiblePieceIds.contains(d.getPiece().getId()))
                .filter(d -> !"VT".equalsIgnoreCase(d.getDefectType()))
                .filter(d -> defectTypeFilter.isEmpty() || matchesDefectTypeFilter(d, defectTypeFilter))
                .filter(d -> statusFilter.isEmpty() || statusFilter.contains(d.getStatus()))
                .filter(d -> matchesWeldingConfirmedFilter(d, weldingFilter))
                .toList();

        Map<UUID, Long> totalPiecesByDefinition = new HashMap<>();
        if (!eligiblePieceIds.isEmpty()) {
            for (Object[] row : pieceRepository.countPiecesGroupedByDefinitionForIds(eligiblePieceIds)) {
                totalPiecesByDefinition.put((UUID) row[0], (Long) row[1]);
            }
        }

        record GroupKey(UUID definitionId, String type) {}

        Map<GroupKey, List<RadiographyDefect>> grouped = new LinkedHashMap<>();
        for (RadiographyDefect d : defects) {
            PieceDefinition pd = d.getPiece().getPieceDefinition();
            GroupKey key = new GroupKey(pd.getId(), DefectTypes.displayLabel(d.getDefectType(), d.getDefectTypeCustom()));
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }

        List<RadiographyDefectReportRow> rows = new ArrayList<>();
        for (Map.Entry<GroupKey, List<RadiographyDefect>> entry : grouped.entrySet()) {
            List<RadiographyDefect> groupDefects = entry.getValue();
            PieceDefinition pd = groupDefects.get(0).getPiece().getPieceDefinition();

            Map<UUID, List<RadiographyDefect>> byPiece = new LinkedHashMap<>();
            for (RadiographyDefect d : groupDefects) {
                byPiece.computeIfAbsent(d.getPiece().getId(), id -> new ArrayList<>()).add(d);
            }

            List<RadiographyDefectReportPieceItem> pieceItems = byPiece.values().stream()
                    .map(pieceDefects -> {
                        Piece piece = pieceDefects.get(0).getPiece();
                        long needsWelding = pieceDefects.stream()
                                .filter(d -> d.getStatus() == DefectStatus.NEEDS_WELDING)
                                .count();
                        long approved = pieceDefects.stream()
                                .filter(d -> d.getStatus() == DefectStatus.APPROVED)
                                .count();
                        long confirmedWeldingCount = pieceDefects.stream()
                                .filter(d -> d.getStatus() == DefectStatus.NEEDS_WELDING && d.isWeldingConfirmed())
                                .count();
                        return new RadiographyDefectReportPieceItem(
                                piece.getId(),
                                piece.getPieceNumber(),
                                piece.getCurrentStage(),
                                pieceDefects.size(),
                                needsWelding,
                                approved,
                                confirmedWeldingCount
                        );
                    })
                    .sorted(Comparator.comparing(RadiographyDefectReportPieceItem::getPieceNumber,
                            Comparator.nullsLast(Integer::compareTo)))
                    .toList();

            long totalInRow = totalPiecesByDefinition.getOrDefault(pd.getId(), 0L);
            long piecesWithDefect = pieceItems.size();
            Double percent = totalInRow == 0
                    ? null
                    : round1(100.0 * piecesWithDefect / totalInRow);

            rows.add(new RadiographyDefectReportRow(
                    project.getCode(),
                    pd.getId(),
                    pd.getRowNum(),
                    pd.getPieceName(),
                    entry.getKey().type(),
                    groupDefects.size(),
                    piecesWithDefect,
                    totalInRow,
                    percent,
                    pieceItems
            ));
        }

        rows.sort(Comparator
                .comparing(RadiographyDefectReportRow::getRowNum, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(RadiographyDefectReportRow::getDefectType, Comparator.nullsLast(String::compareTo)));

        return new RadiographyDefectReportResponse(projectId, project.getCode(), rows);
    }

    private Set<UUID> resolveEligiblePieceIdsForDefectReport(
            UUID projectId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        boolean hasDateFilter = dateFrom != null || dateTo != null;
        if (hasDateFilter) {
            LocalDateTime from = dateFrom != null
                    ? dateFrom.atStartOfDay()
                    : LocalDateTime.of(1970, 1, 1, 0, 0);
            LocalDateTime toExclusive = dateTo != null
                    ? dateTo.plusDays(1).atStartOfDay()
                    : LocalDateTime.now().plusYears(100);
            return new HashSet<>(pieceStageHistoryRepository.findDistinctPieceIdsByStageEntry(
                    projectId,
                    PieceStage.CASTING,
                    from,
                    toExclusive,
                    null
            ));
        }

        return new HashSet<>(pieceRepository.findIdsByProjectExcludingStage(
                projectId,
                PieceStage.NOT_STARTED,
                null
        ));
    }

    private boolean matchesDefectTypeFilter(RadiographyDefect defect, Set<String> filter) {
        if (filter.contains(defect.getDefectType())) {
            return true;
        }
        String display = DefectTypes.displayLabel(defect.getDefectType(), defect.getDefectTypeCustom());
        return filter.contains(display);
    }

    private Set<String> normalizeDefectTypeFilter(List<String> defectTypes) {
        if (defectTypes == null || defectTypes.isEmpty()) {
            return Set.of();
        }
        return defectTypes.stream()
                .filter(t -> t != null && !t.isBlank() && !"VT".equalsIgnoreCase(t.trim()))
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<DefectStatus> normalizeStatusFilter(List<DefectStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(statuses);
    }

    private Set<Boolean> normalizeWeldingConfirmedFilter(List<Boolean> weldingConfirmedFilters) {
        if (weldingConfirmedFilters == null || weldingConfirmedFilters.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(weldingConfirmedFilters);
    }

    private boolean matchesWeldingConfirmedFilter(RadiographyDefect defect, Set<Boolean> weldingFilter) {
        if (weldingFilter.isEmpty()) {
            return true;
        }
        if (defect.getStatus() == DefectStatus.APPROVED) {
            return true;
        }
        return weldingFilter.contains(defect.isWeldingConfirmed());
    }
}
