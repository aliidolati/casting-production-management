package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewResponse {
    private UUID projectId;
    private LocalDate dateFrom;
    private LocalDate dateTo;

    private long totalProjects;
    private long totalDefinitions;
    private long totalPieces;
    private Map<PieceStage, Long> stageCounts = new EnumMap<>(PieceStage.class);

    /** قطعات در جریان تولید (بدون شروع‌نشده / تحویل / اسقاط) */
    private long wipCount;
    private double wipPercent;

    /** تحویل و آماده‌شده در بازهٔ انتخابی (بر اساس تاریخ ورود به همان وضعیت) */
    private long deliveredInPeriod;
    private long readyInPeriod;

    /** قطعاتی که در بازهٔ انتخابی وارد شات‌بلاست یا مرحلهٔ اول عملیات حرارتی شده‌اند */
    private long shotBlastingInPeriod;
    private long heatTreatmentStage1InPeriod;

    /** میانگین روز از اولین حرکت مرحله تا READY؛ فقط قطعات آماده‌تحویل در بازهٔ مرتبط */
    private Double avgCycleTimeDays;

    /** اسقاط فعلی / کل قطعات به‌جز شروع‌نشده؛ شامل بایگانی‌ها و با فیلتر پروژه */
    private Double scrapRatePercent;
    /** تعداد قطعاتی که الان در وضعیت اسقاط هستند (همان محدودهٔ فیلتر پروژه) */
    private long scrappedInPeriod;
    private long finishedInPeriod;

    private BottleneckItem bottleneckProject;
    private BottleneckItem bottleneckStage;

    private List<NeedsWeldingAlertItem> needsWelding;
    private List<StuckPieceAlertItem> stuckPieces;
    private List<SubStageOverdueAlertItem> awaitingInterpretationOverdue;
    private List<SubStageOverdueAlertItem> awaitingOperationsOverdue;
    private int stuckThresholdDays;
    private int subStageThresholdDays;

    private List<RecentStageMoveItem> recentStageMoves;

    /**
     * لیست‌های هشدار بالا برای کم‌کردن حجم پاسخ سقف دارند؛ تعداد واقعی (قبل از برش) این‌جاست
     * تا شمارنده‌ی ویجت‌ها درست بماند.
     */
    private long needsWeldingTotal;
    private long stuckPiecesTotal;
    private long awaitingInterpretationOverdueTotal;
    private long awaitingOperationsOverdueTotal;
}
