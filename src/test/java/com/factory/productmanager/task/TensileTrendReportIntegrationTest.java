package com.factory.productmanager.task;

import com.factory.productmanager.model.dto.TensileTrendReportResponse;
import com.factory.productmanager.model.dto.TensileTrendSeriesResponse;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.TensileTestRepository;
import com.factory.productmanager.service.TensileTrendService;
import com.factory.productmanager.service.trend.TrendConfidence;
import com.factory.productmanager.service.trend.TrendDirection;
import com.factory.productmanager.service.trend.TrendInsufficientReason;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * تست یکپارچه‌ی گزارش «روند تست کشش» روی داده‌ی واقعی در دیتابیس تست (H2):
 * کوئری، فیلترها، محاسبه‌ی روند برای سه پارامتر مستقل و قرارداد JSON.
 */
class TensileTrendReportIntegrationTest extends TaskTestBase {

    private static final LocalDate BASE = LocalDate.of(2026, 1, 1);

    @Autowired private TensileTestRepository tensileTestRepository;
    @Autowired private TensileTrendService trendService;
    @Autowired private EntityManager em;

    private Project project;
    private PieceDefinition definition;
    private int pieceCounter;

    @BeforeEach
    void seedProject() {
        String code = "TR" + UUID.randomUUID().toString().substring(0, 6);
        project = new Project();
        project.setCode(code);
        project.setName("پروژه " + code);
        project = projectRepository.saveAndFlush(project);

        definition = new PieceDefinition();
        definition.setProject(project);
        definition.setRowNum(1);
        definition.setPieceName("قطعه‌ی روند");
        definition.setMinUts(new BigDecimal("250"));
        definition = definitionRepository.saveAndFlush(definition);
        pieceCounter = 0;
    }

    /** یک تست با یک نمونه در روز مشخص (offset از BASE) می‌سازد. */
    private void addTest(int dayOffset, String label, Double uts, Double ys, Double el) {
        Piece piece = new Piece();
        piece.setPieceDefinition(definition);
        piece.setPieceNumber(++pieceCounter);
        piece = pieceRepository.saveAndFlush(piece);

        TensileTest test = new TensileTest();
        test.setPiece(piece);
        test.setTestIndex(1);
        test.setStatus(TensileTestStatus.PENDING_REVIEW);
        TensileTestSample sample = new TensileTestSample();
        sample.setTensileTest(test);
        sample.setSampleIndex(1);
        sample.setSampleLabel(label);
        sample.setUts(uts == null ? null : BigDecimal.valueOf(uts));
        sample.setYs(ys == null ? null : BigDecimal.valueOf(ys));
        sample.setEl(el == null ? null : BigDecimal.valueOf(el));
        test.getSamples().add(sample);
        test = tensileTestRepository.saveAndFlush(test);

        // created_at با @PrePersist برابر «اکنون» می‌شود؛ برای ساخت تاریخچه آن را عقب می‌بریم
        em.createQuery("update TensileTest t set t.createdAt = :d where t.id = :id")
                .setParameter("d", BASE.plusDays(dayOffset).atTime(10, 0))
                .setParameter("id", test.getId())
                .executeUpdate();
        em.flush();
        em.clear();
    }

    private static TensileTrendSeriesResponse series(TensileTrendReportResponse report, String parameter) {
        return report.series().stream().filter(s -> s.parameter().equals(parameter)).findFirst().orElseThrow();
    }

    private TensileTrendReportResponse run(LocalDate from, LocalDate to) {
        return trendService.getReport(project.getId(), definition.getId(), null, from, to, null, null);
    }

    @Test
    void eachParameterGetsItsOwnIndependentTrend() {
        // UTS صعودی، YS تقریباً ثابت، EL نزولی
        double[] uts = {300, 304, 309, 313, 318, 322, 327, 331};
        double[] ys = {200.0, 200.4, 199.8, 200.2, 199.9, 200.1, 200.3, 199.7};
        double[] el = {9.0, 8.6, 8.1, 7.7, 7.2, 6.8, 6.3, 5.9};
        for (int i = 0; i < uts.length; i++) {
            addTest(i * 3, "C4-1", uts[i], ys[i], el[i]);
        }

        TensileTrendReportResponse report = run(null, null);

        assertEquals(8, report.sampleCount());
        assertEquals(TrendDirection.INCREASING, series(report, "UTS").trend());
        assertEquals(TrendDirection.STABLE, series(report, "YS").trend());
        assertEquals(TrendDirection.DECREASING, series(report, "EL").trend());

        TensileTrendSeriesResponse utsSeries = series(report, "UTS");
        assertEquals(8, utsSeries.pointCount());
        assertEquals(300.0, utsSeries.firstValue());
        assertEquals(331.0, utsSeries.lastValue());
        assertEquals(10.33, utsSeries.percentageChange(), 0.01);
        assertTrue(utsSeries.slope() > 0);
        assertEquals("MPa/day", utsSeries.slopeUnit());
        assertTrue(utsSeries.r2() > 0.95);
        assertEquals(TrendConfidence.HIGH, utsSeries.confidence());
        assertTrue(utsSeries.points().stream().allMatch(p -> p.trendValue() != null));
        assertEquals("%", series(report, "EL").unit());
    }

    @Test
    void slopeIsComputedFromRealDatesWhenSamplingIsIrregular() {
        // UTS دقیقاً روزی ۲ MPa زیاد می‌شود؛ نمونه‌ها در روزهای نامنظم‌اند
        int[] days = {0, 1, 2, 10, 11, 30};
        for (int day : days) {
            addTest(day, "C4-1", 300.0 + 2 * day, 200.0, 8.0);
        }
        TensileTrendSeriesResponse uts = series(run(null, null), "UTS");
        assertEquals(2.0, uts.slope(), 1e-4);
        assertEquals(1.0, uts.r2(), 1e-4);
    }

    @Test
    void singleTestIsInsufficientData() {
        addTest(0, "C4-1", 310.0, 205.0, 7.5);
        TensileTrendReportResponse report = run(null, null);
        for (String parameter : List.of("UTS", "YS", "EL")) {
            TensileTrendSeriesResponse s = series(report, parameter);
            assertEquals(TrendDirection.INSUFFICIENT_DATA, s.trend());
            assertEquals(TrendInsufficientReason.SINGLE_POINT, s.insufficientReason());
            assertNull(s.slope());
            assertNull(s.r2());
            assertEquals(1, s.pointCount());
        }
    }

    @Test
    void dateRangeFiltersOnlyRequestedWindow() {
        for (int i = 0; i < 10; i++) {
            addTest(i, "C4-1", 300.0 + i, 200.0, 8.0);
        }
        // روز ۲ تا ۵ (هر دو سر بازه شامل می‌شوند) → ۴ تست
        TensileTrendReportResponse report = run(BASE.plusDays(2), BASE.plusDays(5));
        assertEquals(4, report.sampleCount());
        TensileTrendSeriesResponse uts = series(report, "UTS");
        assertEquals(302.0, uts.firstValue());
        assertEquals(305.0, uts.lastValue());
    }

    @Test
    void plateLabelFilterIsApplied() {
        addTest(0, "C4-1", 300.0, 200.0, 8.0);
        addTest(1, "C3-1", 310.0, 200.0, 8.0);
        addTest(2, "C4-1", 320.0, 200.0, 8.0);

        TensileTrendReportResponse report = trendService.getReport(
                project.getId(), definition.getId(), List.of("C4-1"), null, null, null, null);
        assertEquals(2, report.sampleCount());
        assertEquals(300.0, series(report, "UTS").firstValue());
        assertEquals(320.0, series(report, "UTS").lastValue());
    }

    @Test
    void sampleMissingOneParameterIsSkippedOnlyForThatSeries() {
        addTest(0, "C4-1", 300.0, 200.0, null);
        addTest(1, "C4-1", 305.0, 201.0, 8.0);
        addTest(2, "C4-1", 310.0, 202.0, 7.0);
        TensileTrendReportResponse report = run(null, null);
        assertEquals(3, report.sampleCount());
        assertEquals(3, series(report, "UTS").pointCount());
        assertEquals(2, series(report, "EL").pointCount());
    }

    @Test
    void limitsComeFromPieceDefinitionOnlyAndNothingIsInvented() {
        addTest(0, "C4-1", 300.0, 200.0, 8.0);
        addTest(1, "C4-1", 301.0, 200.0, 8.0);
        TensileTrendReportResponse report = run(null, null);
        assertEquals(0, new BigDecimal("250").compareTo(series(report, "UTS").limits().min()));
        assertNull(series(report, "UTS").limits().max());
        assertNull(series(report, "YS").limits().min());
        assertNull(series(report, "EL").limits().min());

        // بدون انتخاب قطعه، حدی نمایش داده نمی‌شود
        TensileTrendReportResponse noPart = trendService.getReport(
                project.getId(), null, null, null, null, null, null);
        assertNull(series(noPart, "UTS").limits().min());
    }

    @Test
    void outliersAreMarkedButKeptInTheSeries() {
        double[] uts = {300, 301, 300, 302, 301, 450, 300, 301, 302, 300};
        for (int i = 0; i < uts.length; i++) {
            addTest(i, "C4-1", uts[i], 200.0, 8.0);
        }
        TensileTrendSeriesResponse s = series(run(null, null), "UTS");
        assertEquals(10, s.pointCount());
        assertEquals(1, s.outlierCount());
        assertTrue(s.points().get(5).outlier());
    }

    @Test
    void movingAverageIsReturnedWhenEnoughData() {
        for (int i = 0; i < 5; i++) {
            addTest(i, "C4-1", 300.0 + i, 200.0, 8.0);
        }
        TensileTrendReportResponse report = trendService.getReport(
                project.getId(), definition.getId(), null, null, null, 3, null);
        assertEquals(3, report.movingAverageWindow());
        var points = series(report, "UTS").points();
        assertNull(points.get(1).movingAverage());
        assertEquals(301.0, points.get(2).movingAverage(), 1e-9);
        assertEquals(303.0, points.get(4).movingAverage(), 1e-9);
    }

    @Test
    void invalidRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> run(BASE.plusDays(5), BASE));
        assertThrows(IllegalArgumentException.class, () -> trendService.getReport(
                project.getId(), null, null, null, null, 1, null));
        assertThrows(IllegalArgumentException.class, () -> trendService.getReport(
                project.getId(), null, null, null, null, null, 150.0));
    }

    @Test
    void endpointReturnsStructuredJsonForAuthorisedUser() throws Exception {
        for (int i = 0; i < 6; i++) {
            addTest(i * 2, "C4-1", 300.0 + 5 * i, 200.0, 9.0 - i * 0.5);
        }
        call(get("/api/dashboard/tensile-trend-report")
                        .param("projectId", project.getId().toString())
                        .param("pieceDefinitionId", definition.getId().toString())
                        .param("dateFrom", BASE.toString())
                        .param("dateTo", BASE.plusDays(30).toString()),
                admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sampleCount").value(6))
                .andExpect(jsonPath("$.samples.length()").value(6))
                .andExpect(jsonPath("$.series.length()").value(3))
                .andExpect(jsonPath("$.series[0].parameter").value("UTS"))
                .andExpect(jsonPath("$.series[0].trend").value("INCREASING"))
                .andExpect(jsonPath("$.series[0].points.length()").value(6))
                .andExpect(jsonPath("$.series[1].parameter").value("YS"))
                .andExpect(jsonPath("$.series[1].trend").value("STABLE"))
                .andExpect(jsonPath("$.series[2].parameter").value("EL"))
                .andExpect(jsonPath("$.series[2].trend").value("DECREASING"));

        call(get("/api/dashboard/tensile-trend-report")
                        .param("dateFrom", BASE.plusDays(5).toString())
                        .param("dateTo", BASE.toString()),
                admin)
                .andExpect(status().isBadRequest());

        // کاربر بدون دسترسی داشبورد
        call(get("/api/dashboard/tensile-trend-report"), outsider)
                .andExpect(status().isForbidden());
    }
}
