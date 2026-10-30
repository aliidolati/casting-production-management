package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.DashboardAlertsResponse;
import com.factory.productmanager.model.dto.DashboardOverviewResponse;
import com.factory.productmanager.model.dto.DashboardStatsResponse;
import com.factory.productmanager.model.dto.NeedsWeldingAlertItem;
import com.factory.productmanager.model.dto.RadiographyDefectReportResponse;
import com.factory.productmanager.model.dto.StageOccupancyReportResponse;
import com.factory.productmanager.model.dto.TensileQualityReportResponse;
import com.factory.productmanager.model.dto.TensileTrendReportResponse;
import com.factory.productmanager.model.entity.DefectStatus;
import com.factory.productmanager.service.DashboardService;
import com.factory.productmanager.service.TensileTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("@permissionChecker.has(authentication, 'VIEW_DASHBOARD')")
public class DashboardController {

    private final DashboardService dashboardService;
    private final TensileTrendService tensileTrendService;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }

    @GetMapping("/alerts")
    public ResponseEntity<DashboardAlertsResponse> getAlerts(
            @RequestParam(name = "stuckThresholdDays", defaultValue = "7") int stuckThresholdDays
    ) {
        return ResponseEntity.ok(dashboardService.getAlerts(stuckThresholdDays));
    }

    @GetMapping("/overview")
    public ResponseEntity<DashboardOverviewResponse> getOverview(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "7") int stuckThresholdDays,
            @RequestParam(defaultValue = "3") int subStageThresholdDays
    ) {
        return ResponseEntity.ok(dashboardService.getOverview(
                projectId,
                dateFrom,
                dateTo,
                stuckThresholdDays,
                subStageThresholdDays
        ));
    }

    @GetMapping("/radiography-defect-report")
    public ResponseEntity<RadiographyDefectReportResponse> getRadiographyDefectReport(
            @RequestParam UUID projectId,
            @RequestParam(required = false) List<UUID> pieceDefinitionIds,
            @RequestParam(required = false) List<String> defectTypes,
            @RequestParam(required = false) List<DefectStatus> statuses,
            @RequestParam(required = false) List<Boolean> weldingConfirmed,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ResponseEntity.ok(dashboardService.getRadiographyDefectReport(
                projectId,
                pieceDefinitionIds,
                defectTypes,
                statuses,
                weldingConfirmed,
                dateFrom,
                dateTo
        ));
    }

    @GetMapping("/stage-occupancy-report")
    public ResponseEntity<StageOccupancyReportResponse> getStageOccupancyReport(
            @RequestParam(required = false) UUID projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ResponseEntity.ok(dashboardService.getStageOccupancyReport(projectId, dateFrom, dateTo));
    }

    @GetMapping("/welding-station-report")
    public ResponseEntity<List<NeedsWeldingAlertItem>> getWeldingStationReport(
            @RequestParam(required = false) UUID projectId
    ) {
        return ResponseEntity.ok(dashboardService.getWeldingStationReport(projectId));
    }

    @GetMapping("/tensile-quality-report")
    public ResponseEntity<TensileQualityReportResponse> getTensileQualityReport(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID pieceDefinitionId,
            @RequestParam(required = false) List<String> sampleLabels,
            @RequestParam(required = false) String sampleLabel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        List<String> labels = new java.util.ArrayList<>();
        if (sampleLabels != null) {
            labels.addAll(sampleLabels);
        }
        if (sampleLabel != null && !sampleLabel.isBlank()) {
            labels.add(sampleLabel);
        }
        return ResponseEntity.ok(dashboardService.getTensileQualityReport(
                projectId,
                pieceDefinitionId,
                labels,
                dateFrom,
                dateTo
        ));
    }

    /** گزارش «روند تست کشش»: تحلیل روند UTS / YS / EL در بازه‌ی زمانی. */
    @GetMapping("/tensile-trend-report")
    public ResponseEntity<TensileTrendReportResponse> getTensileTrendReport(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID pieceDefinitionId,
            @RequestParam(required = false) List<String> sampleLabels,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Integer movingAverageWindow,
            @RequestParam(required = false) Double stableThresholdPercent
    ) {
        return ResponseEntity.ok(tensileTrendService.getReport(
                projectId,
                pieceDefinitionId,
                sampleLabels,
                dateFrom,
                dateTo,
                movingAverageWindow,
                stableThresholdPercent
        ));
    }
}
