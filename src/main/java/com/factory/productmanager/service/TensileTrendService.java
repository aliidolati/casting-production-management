package com.factory.productmanager.service;

import com.factory.productmanager.config.TensileTrendProperties;
import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.TensileTrendLimitsResponse;
import com.factory.productmanager.model.dto.TensileTrendPointResponse;
import com.factory.productmanager.model.dto.TensileTrendReportResponse;
import com.factory.productmanager.model.dto.TensileTrendSampleResponse;
import com.factory.productmanager.model.dto.TensileTrendSeriesResponse;
import com.factory.productmanager.model.entity.PieceDefinition;
import com.factory.productmanager.repository.PieceDefinitionRepository;
import com.factory.productmanager.repository.TensileTestRepository;
import com.factory.productmanager.service.trend.TensileParameter;
import com.factory.productmanager.service.trend.TensileTrendSampleRow;
import com.factory.productmanager.service.trend.TrendAnalyzer;
import com.factory.productmanager.service.trend.TrendConfig;
import com.factory.productmanager.service.trend.TrendPoint;
import com.factory.productmanager.service.trend.TrendResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * گزارش «روند تست کشش»: برای UTS / YS / EL در یک بازه‌ی زمانی، خط روند، جهت، شیب، R²، درصد تغییر
 * و ... را محاسبه می‌کند. منطق ریاضی در {@link TrendAnalyzer} است و برای هر سه پارامتر مشترک است.
 *
 * <p>مبنای زمان هر نقطه، زمان ثبت تست (tensile_tests.created_at) است؛ همان مبنایی که گزارش
 * Quality Index و فیلتر تاریخ آن استفاده می‌کند.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TensileTrendService {

    private static final double SECONDS_PER_DAY = 86_400.0;
    private static final int MIN_MOVING_AVERAGE_WINDOW = 2;
    private static final int MAX_MOVING_AVERAGE_WINDOW = 50;

    private final TensileTestRepository tensileTestRepository;
    private final PieceDefinitionRepository pieceDefinitionRepository;
    private final TensileTrendProperties properties;

    public TensileTrendReportResponse getReport(
            UUID projectId,
            UUID pieceDefinitionId,
            List<String> sampleLabels,
            LocalDate dateFrom,
            LocalDate dateTo,
            Integer movingAverageWindow,
            Double stableThresholdPercentOverride
    ) {
        if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("تاریخ پایان نمی‌تواند قبل از تاریخ شروع باشد");
        }
        int window = movingAverageWindow == null ? properties.getDefaultMovingAverageWindow() : movingAverageWindow;
        if (window < MIN_MOVING_AVERAGE_WINDOW || window > MAX_MOVING_AVERAGE_WINDOW) {
            throw new IllegalArgumentException(
                    "پنجره‌ی میانگین متحرک باید بین " + MIN_MOVING_AVERAGE_WINDOW + " و " + MAX_MOVING_AVERAGE_WINDOW + " باشد"
            );
        }
        if (stableThresholdPercentOverride != null
                && (stableThresholdPercentOverride.isNaN()
                || stableThresholdPercentOverride < 0 || stableThresholdPercentOverride > 100)) {
            throw new IllegalArgumentException("آستانه‌ی ثابت بودن باید عددی بین ۰ و ۱۰۰ درصد باشد");
        }

        PieceDefinition definition = pieceDefinitionId == null
                ? null
                : pieceDefinitionRepository.findById(pieceDefinitionId)
                        .orElseThrow(() -> new ResourceNotFoundException("قطعه پیدا نشد"));

        List<String> labels = sampleLabels == null
                ? List.of()
                : sampleLabels.stream()
                        .filter(label -> label != null && !label.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();
        boolean noLabelFilter = labels.isEmpty();

        LocalDateTime from = dateFrom == null ? null : dateFrom.atStartOfDay();
        LocalDateTime toExclusive = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay();

        int maxPoints = properties.getMaxPoints();
        List<TensileTrendSampleRow> rows = tensileTestRepository.findTrendRows(
                projectId, pieceDefinitionId, noLabelFilter, noLabelFilter ? List.of("") : labels,
                from, toExclusive, PageRequest.of(0, maxPoints + 1)
        );
        if (rows.size() > maxPoints) {
            throw new IllegalArgumentException(
                    "تعداد نمونه‌های این بازه بیش از " + maxPoints + " است؛ لطفاً بازه یا فیلترها را محدودتر کنید"
            );
        }

        List<TensileTrendSampleResponse> samples = rows.stream().map(TensileTrendService::toSample).toList();
        List<TensileTrendSeriesResponse> series = new ArrayList<>();
        for (TensileParameter parameter : TensileParameter.values()) {
            series.add(buildSeries(parameter, rows, definition, window, stableThresholdPercentOverride));
        }

        return new TensileTrendReportResponse(
                dateFrom,
                dateTo,
                rows.size(),
                window,
                properties.getStrongR2(),
                properties.getModerateR2(),
                properties.getMinReliablePoints(),
                samples,
                series
        );
    }

    private TensileTrendSeriesResponse buildSeries(
            TensileParameter parameter,
            List<TensileTrendSampleRow> allRows,
            PieceDefinition definition,
            int movingAverageWindow,
            Double thresholdOverride
    ) {
        List<TensileTrendSampleRow> rows = allRows.stream().filter(r -> parameter.valueOf(r) != null).toList();

        List<TrendPoint> trendPoints = new ArrayList<>(rows.size());
        List<Double> values = new ArrayList<>(rows.size());
        for (TensileTrendSampleRow row : rows) {
            double value = parameter.valueOf(row).doubleValue();
            trendPoints.add(new TrendPoint(toDays(row.testedAt()), value));
            values.add(value);
        }

        TrendConfig config = properties.toConfig(parameter.name());
        if (thresholdOverride != null) {
            config = config.withStableThresholdPercent(thresholdOverride);
        }
        TrendResult result = TrendAnalyzer.analyze(trendPoints, config);
        List<Double> movingAverage = TrendAnalyzer.movingAverage(values, movingAverageWindow);

        List<TensileTrendPointResponse> points = new ArrayList<>(rows.size());
        int outlierCount = 0;
        for (int i = 0; i < rows.size(); i++) {
            TensileTrendSampleRow row = rows.get(i);
            boolean outlier = result.outliers().get(i);
            if (outlier) {
                outlierCount++;
            }
            points.add(new TensileTrendPointResponse(
                    row.sampleId(),
                    row.testedAt(),
                    values.get(i),
                    result.fittedValues().isEmpty() ? null : round(result.fittedValues().get(i), 3),
                    round(movingAverage.get(i), 3),
                    outlier
            ));
        }

        BigDecimal minLimit = definition == null ? null : parameter.minLimitOf(definition);

        return new TensileTrendSeriesResponse(
                parameter.name(),
                parameter.unit(),
                result.count(),
                points,
                round(result.average(), 2),
                round(result.firstValue(), 2),
                round(result.lastValue(), 2),
                round(result.percentageChange(), 2),
                round(result.slopePerDay(), 6),
                parameter.unit() + "/day",
                round(result.r2(), 4),
                round(result.normalizedChangePercent(), 2),
                config.stableThresholdPercent(),
                result.direction(),
                result.confidence(),
                result.insufficientReason(),
                outlierCount,
                // max و target عمداً null‌اند: منبعی برایشان تعریف نشده و مقدار فرضی نمی‌گذاریم
                new TensileTrendLimitsResponse(minLimit, null, null)
        );
    }

    private static TensileTrendSampleResponse toSample(TensileTrendSampleRow row) {
        return new TensileTrendSampleResponse(
                row.sampleId(),
                row.tensileTestId(),
                row.testedAt(),
                row.sampleLabel(),
                row.projectCode(),
                row.pieceName(),
                row.pieceNumber(),
                row.uts(),
                row.ys(),
                row.el()
        );
    }

    /** زمان واقعی به «روز» اعشاری؛ از UTC استفاده می‌شود تا تغییر ساعت تابستانی شیب را به‌هم نزند. */
    private static double toDays(LocalDateTime time) {
        return time.toEpochSecond(ZoneOffset.UTC) / SECONDS_PER_DAY;
    }

    private static Double round(Double value, int scale) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
