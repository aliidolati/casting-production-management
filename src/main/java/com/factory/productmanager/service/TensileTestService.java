package com.factory.productmanager.service;

import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.CreateTensileTestRequest;
import com.factory.productmanager.model.dto.PendingTensileReviewItem;
import com.factory.productmanager.model.dto.SaveTensileDispositionRequest;
import com.factory.productmanager.model.dto.TensileDispositionResponse;
import com.factory.productmanager.model.dto.TensileSampleDto;
import com.factory.productmanager.model.dto.TensileTestResponse;
import com.factory.productmanager.model.entity.HeatTreatmentSubStage;
import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.PieceDefinition;
import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.TensileInspectionResult;
import com.factory.productmanager.model.entity.TensileNextStage;
import com.factory.productmanager.model.entity.TensileSampleLabels;
import com.factory.productmanager.model.entity.TensileTest;
import com.factory.productmanager.model.entity.TensileTestDisposition;
import com.factory.productmanager.model.entity.TensileTestSample;
import com.factory.productmanager.model.entity.TensileTestStatus;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.PieceRepository;
import com.factory.productmanager.repository.TensileTestDispositionRepository;
import com.factory.productmanager.repository.TensileTestRepository;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class TensileTestService {

    /** نامی که در فیلدهای «ثبت‌کننده/بررسی‌کننده» برای اقدام‌های خودکار نوشته می‌شود */
    private static final String SYSTEM_ACTOR = "سیستم (خودکار)";

    private final TensileTestRepository tensileTestRepository;
    private final TensileTestDispositionRepository tensileTestDispositionRepository;
    private final PieceRepository pieceRepository;
    private final PieceService pieceService;
    private final ScrapService scrapService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * ثبت یک تست کشش جدید برای یک قطعه. نتیجه بلافاصله با آستانه‌های تعریف قطعه
     * مقایسه می‌شود: اگر همه‌ی آستانه‌ها برآورده شده باشند تست خودکار تایید می‌شود،
     * وگرنه در وضعیت «در انتظار تعیین‌تکلیف» برای مسئول کشش می‌ماند.
     */
    public TensileTestResponse createTest(UUID pieceId, CreateTensileTestRequest request, String username) {
        Piece piece = pieceRepository.lockById(pieceId)
                .orElseThrow(() -> new ResourceNotFoundException("قطعه پیدا نشد"));

        if (piece.getCurrentStage() != PieceStage.TENSILE) {
            throw new IllegalStateException(
                    "ثبت تست کشش فقط برای قطعاتی که در مرحله‌ی تست کشش هستند امکان‌پذیر است"
            );
        }

        TensileTest test = new TensileTest();
        test.setPiece(piece);
        test.setTestIndex(tensileTestRepository.findMaxTestIndexByPieceId(pieceId) + 1);
        test.setStatus(TensileTestStatus.PENDING_REVIEW);
        test.setCreatedBy(resolveDisplayName(username));

        int index = 1;
        for (TensileSampleDto dto : request.getSamples()) {
            TensileTestSample sample = new TensileTestSample();
            sample.setTensileTest(test);
            sample.setSampleIndex(index++);
            applySampleValues(sample, dto);
            test.getSamples().add(sample);
        }

        ThresholdEvaluation evaluation = evaluateThresholds(test, piece.getPieceDefinition());
        test.setBelowThreshold(!evaluation.failures().isEmpty());
        test.setThresholdFailures(
                evaluation.failures().isEmpty() ? null : String.join(",", evaluation.failures())
        );

        if (evaluation.passesCompletely()) {
            applySystemApproval(test);
        }

        TensileTest saved = tensileTestRepository.save(test);
        if (saved.getDisposition() != null && saved.getDisposition().isSystemGenerated()) {
            applyTensileStageMove(saved.getPiece().getId(), TensileNextStage.APPROVED, username);
            notificationService.createTensileAutoApproved(saved);
        }
        return toResponse(saved);
    }

    /**
     * اصلاح مقادیر نمونه‌ها. وضعیت و تعیین‌تکلیف عوض نمی‌شود، چه تست هنوز
     * در انتظار بررسی باشد و چه مسئول کشش روی آن تصمیم گرفته باشد.
     */
    public TensileTestResponse updateTest(UUID tensileTestId, CreateTensileTestRequest request) {
        TensileTest test = lockTestWithPiece(tensileTestId);
        List<TensileTestSample> samples = test.getSamples();
        List<TensileSampleDto> incoming = request.getSamples();

        for (int i = 0; i < incoming.size(); i++) {
            TensileTestSample sample;
            if (i < samples.size()) {
                sample = samples.get(i);
            } else {
                sample = new TensileTestSample();
                sample.setTensileTest(test);
                samples.add(sample);
            }
            sample.setSampleIndex(i + 1);
            applySampleValues(sample, incoming.get(i));
        }
        while (samples.size() > incoming.size()) {
            samples.remove(samples.size() - 1);
        }

        ThresholdEvaluation evaluation = evaluateThresholds(test, test.getPiece().getPieceDefinition());
        test.setBelowThreshold(!evaluation.failures().isEmpty());
        test.setThresholdFailures(
                evaluation.failures().isEmpty() ? null : String.join(",", evaluation.failures())
        );

        return toResponse(tensileTestRepository.save(test));
    }

    @Transactional(readOnly = true)
    public List<TensileTestResponse> getTestsByPiece(UUID pieceId) {
        findPieceOrThrow(pieceId);
        return tensileTestRepository.findByPieceIdOrderByTestIndexAsc(pieceId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * ثبت یا ویرایش تعیین‌تکلیف یک تست. وضعیت نهایی تست از «نتیجه بازرسی»
     * گرفته می‌شود. اصلاح سابقه قطعه را جابه‌جا نمی‌کند؛ انتقال نیازمند تصمیم جاری و معتبر است.
     */
    public TensileTestResponse saveDisposition(UUID tensileTestId, SaveTensileDispositionRequest request, String username) {
        TensileTest test = lockTestWithPiece(tensileTestId);

        TensileTestDisposition disposition = test.getDisposition();
        boolean existingDecision = disposition != null;
        Piece piece = test.getPiece();
        var latest = tensileTestRepository.findLatestForUpdate(piece.getId(), org.springframework.data.domain.PageRequest.of(0, 1));
        boolean eligibleMovement = piece.getCurrentStage() == PieceStage.TENSILE && piece.getArchivedAt() == null
                && !latest.isEmpty() && latest.get(0).getId().equals(test.getId());
        boolean movePiece = Boolean.TRUE.equals(request.getApplyStageMove())
                || (request.getApplyStageMove() == null && !existingDecision && eligibleMovement);
        if (Boolean.TRUE.equals(request.getApplyStageMove()) && !eligibleMovement) {
            throw new IllegalStateException("اعمال انتقال فقط برای آخرین تست و قطعه فعال در مرحله کشش مجاز است؛ اصلاح تاریخی را بدون انتقال ذخیره کنید");
        }
        if (disposition == null) {
            disposition = new TensileTestDisposition();
            disposition.setTensileTest(test);
            disposition.setCreatedBy(resolveDisplayName(username));
            test.setDisposition(disposition);
        }

        disposition.setDispositionDate(request.getDispositionDate());
        disposition.setQuantity(request.getQuantity());
        disposition.setDurationHours(request.getDurationHours());
        disposition.setTemperatureC(request.getTemperatureC());
        disposition.setNextStage(request.getNextStage());
        disposition.setInspectionResult(request.getInspectionResult());
        disposition.setNotes(trimToNull(request.getNotes()));
        disposition.setResponsibleUser(resolveResponsibleUser(request.getResponsibleUserId(), username));
        // ویرایش دستی روی تایید خودکار، آن را به یک تعیین‌تکلیف انسانی تبدیل می‌کند
        disposition.setSystemGenerated(false);

        test.setStatus(request.getInspectionResult() == TensileInspectionResult.ACCEPTED
                ? TensileTestStatus.APPROVED
                : TensileTestStatus.REJECTED);
        test.setReviewedAt(LocalDateTime.now());
        test.setReviewedBy(resolveDisplayName(username));

        TensileTest saved = tensileTestRepository.save(test);
        if (movePiece) applyTensileStageMove(saved.getPiece().getId(), request.getNextStage(), username, request.getScrap());
        return toResponse(saved);
    }

    /**
     * حذف یک تست برای اصلاح اشتباه اپراتور. تستی که مسئول کشش روی آن تصمیم گرفته
     * حذف نمی‌شود تا تاریخچه‌ی تحلیلی دست‌نخورده بماند؛ ولی تاییدِ خودکارِ سیستم
     * مانع اصلاح نیست، چون هنوز هیچ انسانی آن را امضا نکرده است.
     */
    public void deleteTest(UUID tensileTestId) {
        TensileTest test = lockTestWithPiece(tensileTestId);
        if (!isDeletable(test)) {
            throw new IllegalStateException("تست کششی که تعیین‌تکلیف شده قابل حذف نیست");
        }
        notificationService.deleteByTensileTestId(test.getId());
        tensileTestRepository.delete(test);
    }

    private static boolean isDeletable(TensileTest test) {
        if (test.getStatus() == TensileTestStatus.PENDING_REVIEW) {
            return true;
        }
        TensileTestDisposition disposition = test.getDisposition();
        return disposition != null && disposition.isSystemGenerated();
    }

    @Transactional(readOnly = true)
    public List<PendingTensileReviewItem> getPendingReview(UUID projectId) {
        LocalDateTime now = LocalDateTime.now();
        return tensileTestRepository.findPendingReview(TensileTestStatus.PENDING_REVIEW, projectId)
                .stream()
                .map(t -> {
                    Piece piece = t.getPiece();
                    PieceDefinition definition = piece.getPieceDefinition();
                    return new PendingTensileReviewItem(
                            t.getId(),
                            t.getTestIndex(),
                            piece.getId(),
                            piece.getPieceNumber(),
                            definition.getId(),
                            definition.getPieceName(),
                            definition.getProject().getId(),
                            definition.getProject().getCode(),
                            t.isBelowThreshold(),
                            t.getCreatedAt(),
                            t.getCreatedBy(),
                            Duration.between(t.getCreatedAt(), now).toDays()
                    );
                })
                .toList();
    }

    // ------------------------------------------------------------------
    // منطق آستانه‌ها
    // ------------------------------------------------------------------

    /**
     * @param failures  پارامترهایی که دست‌کم در یک نمونه زیر حد مجاز بوده‌اند
     * @param conclusive آیا مقایسه کامل بوده؛ یعنی تعریف قطعه دست‌کم یک آستانه دارد و
     *                   برای هر آستانه‌ی تعریف‌شده، مقدار همه‌ی نمونه‌ها وارد شده است.
     *                   اگر داده ناقص باشد نمی‌توان با اطمینان تایید خودکار داد.
     */
    private record ThresholdEvaluation(Set<String> failures, boolean conclusive) {
        boolean passesCompletely() {
            return conclusive && failures.isEmpty();
        }
    }

    /**
     * هر نمونه جداگانه با آستانه‌های تعریف قطعه مقایسه می‌شود؛ اگر حتی یک مقدار
     * در یک نمونه زیر حد مجاز باشد، پرچم هشدار کل تست فعال می‌شود.
     * پارامتری که آستانه ندارد نادیده گرفته می‌شود، ولی اگر آستانه دارد و مقدارش
     * وارد نشده، نتیجه ناقص محسوب می‌شود و تست برای بررسی دستی می‌ماند.
     */
    private ThresholdEvaluation evaluateThresholds(TensileTest test, PieceDefinition definition) {
        Set<String> failures = new LinkedHashSet<>();
        boolean anyThreshold = false;
        boolean anyMissingValue = false;

        BigDecimal[] minimums = {
                definition.getMinUts(), definition.getMinYs(), definition.getMinEl(), definition.getMinHb()
        };
        String[] names = {"UTS", "YS", "EL", "HB"};

        for (int i = 0; i < minimums.length; i++) {
            BigDecimal minimum = minimums[i];
            if (minimum == null) {
                continue;
            }
            anyThreshold = true;

            for (TensileTestSample sample : test.getSamples()) {
                BigDecimal value = valueOf(sample, i);
                if (value == null) {
                    anyMissingValue = true;
                } else if (value.compareTo(minimum) < 0) {
                    failures.add(names[i]);
                }
            }
        }

        return new ThresholdEvaluation(failures, anyThreshold && !anyMissingValue);
    }

    private static BigDecimal valueOf(TensileTestSample sample, int parameterIndex) {
        return switch (parameterIndex) {
            case 0 -> sample.getUts();
            case 1 -> sample.getYs();
            case 2 -> sample.getEl();
            default -> sample.getHb();
        };
    }

    /**
     * تستی که همه‌ی آستانه‌ها را برآورده کرده بدون معطلی توسط سیستم تایید می‌شود
     * تا در صف تعیین‌تکلیف مسئول کشش ننشیند.
     */
    /**
     * تایید / تایید ارفاقی → شات‌بلاست؛ اسقاط → اسقاط؛ عملیات مجدد → منتظر عملیات؛
     * پیرسازی → مرحله سوم/چهارم/پنجم. کشش بعدی و سایر قطعه را در کشش نگه می‌دارند.
     */
    private void applyTensileStageMove(UUID pieceId, TensileNextStage nextStage, String username) {
        applyTensileStageMove(pieceId,nextStage,username,null);
    }
    private void applyTensileStageMove(UUID pieceId, TensileNextStage nextStage, String username,
            com.factory.productmanager.model.dto.ScrapRequest scrap) {
        if (nextStage == null) {
            return;
        }
        Piece piece = findPieceOrThrow(pieceId);
        if (piece.getCurrentStage() == PieceStage.WELDING) {
            throw new IllegalStateException("قطعه در ایستگاه جوشکاری است؛ اول فرآیند جوش را تمام کنید");
        }

        switch (nextStage) {
            case APPROVED, CONCESSION_APPROVAL -> moveIfNeeded(piece, PieceStage.SHOT_BLASTING, null, username);
            case SCRAP -> {
                if (piece.getCurrentStage() == PieceStage.SCRAPPED) {
                    scrapService.edit(pieceId, scrap, username);
                } else {
                    pieceService.applyTrustedStageMove(pieceId, PieceStage.SCRAPPED, username, scrap);
                }
            }
            case RE_OPERATION -> moveIfNeeded(
                    piece, PieceStage.HEAT_TREATMENT, HeatTreatmentSubStage.AWAITING_OPERATIONS, username
            );
            case AGING -> {
                long agingCount = tensileTestDispositionRepository.countByPieceIdAndNextStage(
                        pieceId, TensileNextStage.AGING
                );
                HeatTreatmentSubStage agingStage = agingCount <= 1
                        ? HeatTreatmentSubStage.STAGE_3
                        : agingCount == 2
                        ? HeatTreatmentSubStage.STAGE_4
                        : HeatTreatmentSubStage.STAGE_5;
                moveIfNeeded(piece, PieceStage.HEAT_TREATMENT, agingStage, username);
            }
            default -> {
            }
        }
    }

    private void moveIfNeeded(
            Piece piece,
            PieceStage target,
            HeatTreatmentSubStage heatTreatmentSubStage,
            String username
    ) {
        if (piece.getCurrentStage() == target) {
            if (target != PieceStage.HEAT_TREATMENT || heatTreatmentSubStage == null) {
                return;
            }
            if (StageTransitionPolicy.heatOf(piece) == heatTreatmentSubStage) {
                return;
            }
        }
        pieceService.applyTrustedStageMove(
                piece.getId(),
                target,
                null,
                heatTreatmentSubStage,
                null,
                username
        );
    }

    private void applySystemApproval(TensileTest test) {
        TensileTestDisposition disposition = new TensileTestDisposition();
        disposition.setTensileTest(test);
        disposition.setDispositionDate(LocalDate.now());
        disposition.setNextStage(TensileNextStage.APPROVED);
        disposition.setInspectionResult(TensileInspectionResult.ACCEPTED);
        disposition.setSystemGenerated(true);
        disposition.setCreatedBy(SYSTEM_ACTOR);
        test.setDisposition(disposition);

        test.setStatus(TensileTestStatus.APPROVED);
        test.setReviewedAt(LocalDateTime.now());
        test.setReviewedBy(SYSTEM_ACTOR);
    }

    // ------------------------------------------------------------------
    // کمکی‌ها
    // ------------------------------------------------------------------

    private TensileTestResponse toResponse(TensileTest test) {
        PieceDefinition definition = test.getPiece().getPieceDefinition();

        List<TensileSampleDto> samples = test.getSamples().stream()
                .map(s -> new TensileSampleDto(
                        s.getSampleIndex(),
                        s.getSampleLabel(),
                        s.getUts(),
                        s.getYs(),
                        s.getEl(),
                        s.getHb(),
                        s.getDescription()
                ))
                .toList();

        List<String> failures = test.getThresholdFailures() == null || test.getThresholdFailures().isBlank()
                ? List.of()
                : Arrays.stream(test.getThresholdFailures().split(",")).toList();

        return new TensileTestResponse(
                test.getId(),
                test.getPiece().getId(),
                test.getTestIndex(),
                test.getStatus(),
                test.getStatus().getLabel(),
                test.isBelowThreshold(),
                new ArrayList<>(failures),
                test.getCreatedAt(),
                test.getCreatedBy(),
                test.getReviewedAt(),
                test.getReviewedBy(),
                new ArrayList<>(samples),
                toDispositionResponse(test.getDisposition()),
                isDeletable(test),
                definition.getMinUts(),
                definition.getMinYs(),
                definition.getMinEl(),
                definition.getMinHb()
        );
    }

    private TensileDispositionResponse toDispositionResponse(TensileTestDisposition disposition) {
        if (disposition == null) {
            return null;
        }
        User responsible = disposition.getResponsibleUser();
        return new TensileDispositionResponse(
                disposition.getId(),
                disposition.getDispositionDate(),
                disposition.getQuantity(),
                disposition.getDurationHours(),
                disposition.getTemperatureC(),
                disposition.getNextStage(),
                disposition.getNextStage().getLabel(),
                disposition.getInspectionResult(),
                disposition.getInspectionResult().getLabel(),
                responsible == null ? null : responsible.getId(),
                responsible == null ? null : displayNameOf(responsible),
                disposition.isSystemGenerated(),
                disposition.getNotes(),
                disposition.getCreatedAt(),
                disposition.getUpdatedAt(),
                disposition.getCreatedBy()
        );
    }

    private User resolveResponsibleUser(UUID requestedUserId, String currentUsername) {
        if (requestedUserId != null) {
            return userRepository.findById(requestedUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("کاربر مسئول انتخاب‌شده پیدا نشد"));
        }
        return userRepository.findByUsername(currentUsername).orElse(null);
    }

    private Piece findPieceOrThrow(UUID pieceId) {
        return pieceRepository.findById(pieceId)
                .orElseThrow(() -> new ResourceNotFoundException("قطعه‌ای با شناسه " + pieceId + " پیدا نشد"));
    }

    /** Consistent lock order: piece first, then tests. Creates use the same piece lock. */
    private TensileTest lockTestWithPiece(UUID testId) {
        UUID pieceId = tensileTestRepository.findPieceIdForTest(testId)
                .orElseThrow(() -> new ResourceNotFoundException("تست کششی پیدا نشد"));
        pieceRepository.lockById(pieceId).orElseThrow(() -> new ResourceNotFoundException("قطعه پیدا نشد"));
        return tensileTestRepository.findByIdForUpdate(testId)
                .orElseThrow(() -> new ResourceNotFoundException("تست کششی پیدا نشد"));
    }



    private String resolveDisplayName(String username) {
        return userRepository.findByUsername(username)
                .map(TensileTestService::displayNameOf)
                .orElse(username);
    }

    private static String displayNameOf(User user) {
        return (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName()
                : user.getUsername();
    }

    private static void applySampleValues(TensileTestSample sample, TensileSampleDto dto) {
        String sampleLabel = trimToNull(dto.getSampleLabel());
        boolean keepLegacyLabel = sampleLabel != null
                && sampleLabel.equals(sample.getSampleLabel())
                && !TensileSampleLabels.isAllowed(sampleLabel);
        if (sampleLabel != null && !TensileSampleLabels.isAllowed(sampleLabel) && !keepLegacyLabel) {
            throw new IllegalArgumentException("شماره نمونه «" + sampleLabel + "» مجاز نیست");
        }
        sample.setSampleLabel(sampleLabel);
        sample.setUts(dto.getUts());
        sample.setYs(dto.getYs());
        sample.setEl(dto.getEl());
        sample.setHb(dto.getHb());
        sample.setDescription(trimToNull(dto.getDescription()));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
