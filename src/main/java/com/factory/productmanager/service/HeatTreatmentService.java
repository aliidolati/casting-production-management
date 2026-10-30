package com.factory.productmanager.service;

import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.CreatePieceHeatTreatmentRequest;
import com.factory.productmanager.model.dto.HeatTreatmentBatchPieceResponse;
import com.factory.productmanager.model.dto.HeatTreatmentBatchResponse;
import com.factory.productmanager.model.dto.PieceHeatTreatmentResponse;
import com.factory.productmanager.model.dto.UpdateHeatTreatmentBatchRequest;
import com.factory.productmanager.model.entity.HeatTreatmentBatch;
import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.PieceHeatTreatment;
import com.factory.productmanager.model.entity.TensileTestDisposition;
import com.factory.productmanager.repository.HeatTreatmentBatchRepository;
import com.factory.productmanager.repository.PieceHeatTreatmentRepository;
import com.factory.productmanager.repository.TensileTestDispositionRepository;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * لاگ عملیات حرارتی: ثبت اینکه یک قطعه در کدام محموله‌ی کوره و با چه سیکلی
 * عملیات شده است.
 *
 * پارامترهای سیکل روی محموله می‌نشینند و داده‌های تست پلیت روی رکورد قطعه.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class HeatTreatmentService {

    private final HeatTreatmentBatchRepository batchRepository;
    private final PieceHeatTreatmentRepository pieceHeatTreatmentRepository;
    private final TensileTestDispositionRepository tensileTestDispositionRepository;
    private final PieceService pieceService;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    /** بخش عددی انتهای کد محموله، برای استنتاج کد بعدی از روی قرارداد خود کاربر */
    private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.*?)(\\d+)$");

    /** سقف تلاش برای یافتن کد آزاد، تا حلقه در داده‌ی نامنتظره بی‌پایان نشود */
    private static final int MAX_CODE_PROBES = 1000;

    /**
     * ثبت حضور یک قطعه در یک محموله. محموله با کدش پیدا می‌شود و اگر وجود نداشته
     * باشد ساخته می‌شود، تا کاربر برای هر قطعه مجبور نباشد اول محموله را جدا بسازد.
     * روی محموله‌ی موجود، پارامترهای سیکل دست‌نخورده می‌مانند؛ یک سیکل که انجام
     * شده با افزودن قطعه‌ی بعدی نباید بازنویسی شود.
     */
    public PieceHeatTreatmentResponse recordForPiece(
            UUID pieceId,
            CreatePieceHeatTreatmentRequest request,
            String username
    ) {
        Piece piece = pieceService.findPieceOrThrow(pieceId);
        HeatTreatmentBatch batch = findOrCreateBatch(request, username);

        if (pieceHeatTreatmentRepository.existsByBatchIdAndPieceId(batch.getId(), pieceId)) {
            throw new IllegalStateException("این قطعه قبلاً در محموله‌ی «" + batch.getBatchCode() + "» ثبت شده است");
        }

        PieceHeatTreatment record = new PieceHeatTreatment();
        record.setPiece(piece);
        record.setBatch(batch);
        record.setTreatmentStage(trimToNull(request.getTreatmentStage()));
        record.setTestPlateLabel(trimToNull(request.getTestPlateLabel()));
        record.setTestPlateHardnessHb(request.getTestPlateHardnessHb());
        record.setDetails(trimToNull(request.getDetails()));
        record.setCreatedBy(resolveDisplayName(username));
        record.setTensileDisposition(resolveDisposition(request.getTensileDispositionId(), pieceId));

        return toResponse(pieceHeatTreatmentRepository.save(record), username);
    }

    @Transactional(readOnly = true)
    public List<PieceHeatTreatmentResponse> getByPiece(UUID pieceId, String username) {
        pieceService.findPieceOrThrow(pieceId);
        return pieceHeatTreatmentRepository.findByPieceIdOrderByCreatedAtDesc(pieceId)
                .stream()
                .map(r -> toResponse(r, username))
                .toList();
    }

    /**
     * محموله‌های ثبت‌شده در همین روز، برای فهرست پیشنهاد در فرم ثبت.
     *
     * عمداً بر اساس created_at فیلتر می‌شود نه treatment_date: هدف این فهرست
     * «محموله‌هایی است که همین حالا دارم رویشان کار می‌کنم»، حتی اگر تاریخ
     * عملیاتشان مربوط به چند روز قبل باشد.
     */
    @Transactional(readOnly = true)
    public List<HeatTreatmentBatchResponse> getTodayBatches() {
        return batchRepository
                .findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(LocalDate.now().atStartOfDay())
                .stream()
                .map(this::toBatchResponse)
                .toList();
    }

    /**
     * استعلام دقیق یک کد محموله. فرم ثبت برای تصمیم‌گیری درباره‌ی قفل‌کردن
     * پارامترهای سیکل از این استفاده می‌کند و نه از فهرست پیشنهادها؛ وگرنه کدِ
     * محموله‌ای که در فهرست نیست «جدید» تشخیص داده می‌شد و مقادیر واردشده‌ی
     * کاربر بی‌صدا نادیده گرفته می‌شد.
     */
    @Transactional(readOnly = true)
    public Optional<HeatTreatmentBatchResponse> findBatchByCode(String batchCode) {
        if (batchCode == null || batchCode.isBlank()) {
            return Optional.empty();
        }
        return batchRepository.findByBatchCode(batchCode.trim()).map(this::toBatchResponseWithPieces);
    }

    public HeatTreatmentBatchResponse updateBatch(UUID batchId, UpdateHeatTreatmentBatchRequest request) {
        HeatTreatmentBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("محموله‌ای با این شناسه پیدا نشد"));
        batch.setTreatmentDate(request.getTreatmentDate());
        batch.setTemperatureC(request.getTemperatureC());
        batch.setDurationHours(request.getDurationHours());
        batch.setFurnaceSize(request.getFurnaceSize());
        batch.setNotes(trimToNull(request.getNotes()));
        return toBatchResponseWithPieces(batchRepository.save(batch));
    }

    /**
     * کد پیشنهادی برای محموله‌ی بعدی: عدد انتهایی کد آخرین محموله یکی زیاد
     * می‌شود و طول صفرهای ابتدایی حفظ می‌گردد (مثلاً HT-1404-012 به HT-1404-013).
     *
     * الگو از روی داده‌ی خود کاربر استنتاج می‌شود، پس هر قراردادِ نام‌گذاری‌ای
     * کار می‌کند و نیازی به تنظیمات ندارد. اگر کد آخر به عدد ختم نشود یا هنوز
     * محموله‌ای ثبت نشده باشد، پیشنهادی داده نمی‌شود.
     */
    @Transactional(readOnly = true)
    public Optional<String> suggestNextBatchCode() {
        return batchRepository.findTopByOrderByCreatedAtDesc()
                .map(HeatTreatmentBatch::getBatchCode)
                .flatMap(this::nextFreeCodeAfter);
    }

    private Optional<String> nextFreeCodeAfter(String lastCode) {
        Matcher matcher = TRAILING_NUMBER.matcher(lastCode);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String prefix = matcher.group(1);
        String digits = matcher.group(2);

        long next;
        try {
            next = Long.parseLong(digits) + 1;
        } catch (NumberFormatException overflow) {
            return Optional.empty();
        }

        // اگر کدها دستی و خارج از ترتیب ساخته شده باشند، ممکن است کد بعدی از قبل
        // وجود داشته باشد؛ جلو می‌رویم تا به یک کد آزاد برسیم
        for (int probe = 0; probe < MAX_CODE_PROBES; probe++, next++) {
            String candidate = prefix + zeroPadded(next, digits.length());
            if (!batchRepository.existsByBatchCode(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static String zeroPadded(long value, int width) {
        String raw = Long.toString(value);
        return raw.length() >= width ? raw : "0".repeat(width - raw.length()) + raw;
    }

    private HeatTreatmentBatchResponse toBatchResponse(HeatTreatmentBatch batch) {
        return toBatchResponse(batch, false);
    }

    private HeatTreatmentBatchResponse toBatchResponseWithPieces(HeatTreatmentBatch batch) {
        return toBatchResponse(batch, true);
    }

    private HeatTreatmentBatchResponse toBatchResponse(HeatTreatmentBatch batch, boolean includePieces) {
        HeatTreatmentBatchResponse response = new HeatTreatmentBatchResponse(
                batch.getId(),
                batch.getBatchCode(),
                batch.getTreatmentDate(),
                batch.getTemperatureC(),
                batch.getDurationHours(),
                batch.getFurnaceSize(),
                batch.getFurnaceSize() == null ? null : batch.getFurnaceSize().getLabel(),
                batch.getNotes(),
                pieceHeatTreatmentRepository.countByBatchId(batch.getId()),
                batch.getCreatedAt(),
                batch.getCreatedBy(),
                List.of()
        );
        if (includePieces) {
            response.setPieces(
                    pieceHeatTreatmentRepository.findWithPieceByBatchIdOrderByCreatedAtAsc(batch.getId())
                            .stream()
                            .map(this::toBatchPieceResponse)
                            .toList()
            );
            response.setPieceCount(response.getPieces().size());
        }
        return response;
    }

    private HeatTreatmentBatchPieceResponse toBatchPieceResponse(PieceHeatTreatment record) {
        Piece piece = record.getPiece();
        return new HeatTreatmentBatchPieceResponse(
                record.getId(),
                piece.getId(),
                piece.getPieceDefinition().getProject().getCode(),
                piece.getPieceDefinition().getRowNum(),
                piece.getPieceNumber(),
                piece.getPieceDefinition().getPieceName(),
                record.getTreatmentStage(),
                record.getTestPlateLabel(),
                record.getTestPlateHardnessHb(),
                record.getDetails()
        );
    }

    /**
     * حذف رکورد برای اصلاح اشتباه ثبت. محموله‌ی خالی‌شده هم پاک می‌شود تا کدهای
     * بی‌استفاده در فهرست پیشنهادها باقی نمانند.
     */
    public void deleteRecord(UUID recordId) {
        PieceHeatTreatment record = pieceHeatTreatmentRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("رکورد عملیات حرارتی با شناسه " + recordId + " پیدا نشد"));

        UUID batchId = record.getBatch().getId();
        pieceHeatTreatmentRepository.delete(record);
        pieceHeatTreatmentRepository.flush();

        if (pieceHeatTreatmentRepository.countByBatchId(batchId) == 0) {
            batchRepository.deleteById(batchId);
        }
    }

    // ------------------------------------------------------------------
    // کمکی‌ها
    // ------------------------------------------------------------------

    private HeatTreatmentBatch findOrCreateBatch(CreatePieceHeatTreatmentRequest request, String username) {
        String batchCode = request.getBatchCode().trim();

        return batchRepository.findByBatchCode(batchCode)
                .orElseGet(() -> {
                    HeatTreatmentBatch batch = new HeatTreatmentBatch();
                    batch.setBatchCode(batchCode);
                    batch.setTreatmentDate(request.getTreatmentDate());
                    batch.setTemperatureC(request.getTemperatureC());
                    batch.setDurationHours(request.getDurationHours());
                    batch.setFurnaceSize(request.getFurnaceSize());
                    batch.setNotes(trimToNull(request.getBatchNotes()));
                    batch.setCreatedBy(resolveDisplayName(username));
                    return batchRepository.save(batch);
                });
    }

    /** تعیین‌تکلیف انتخاب‌شده باید متعلق به همین قطعه باشد، وگرنه لینک بی‌معناست. */
    private TensileTestDisposition resolveDisposition(UUID dispositionId, UUID pieceId) {
        if (dispositionId == null) {
            return null;
        }

        TensileTestDisposition disposition = tensileTestDispositionRepository.findById(dispositionId)
                .orElseThrow(() -> new ResourceNotFoundException("تعیین‌تکلیف کشش انتخاب‌شده پیدا نشد"));

        if (!disposition.getTensileTest().getPiece().getId().equals(pieceId)) {
            throw new IllegalArgumentException("تعیین‌تکلیف انتخاب‌شده مربوط به این قطعه نیست");
        }
        return disposition;
    }

    private PieceHeatTreatmentResponse toResponse(PieceHeatTreatment record, String username) {
        HeatTreatmentBatch batch = record.getBatch();
        TensileTestDisposition disposition = record.getTensileDisposition();

        return new PieceHeatTreatmentResponse(
                record.getId(),
                record.getPiece().getId(),
                batch.getId(),
                batch.getBatchCode(),
                batch.getTreatmentDate(),
                batch.getTemperatureC(),
                batch.getDurationHours(),
                batch.getFurnaceSize(),
                batch.getFurnaceSize() == null ? null : batch.getFurnaceSize().getLabel(),
                batch.getNotes(),
                pieceHeatTreatmentRepository.countByBatchId(batch.getId()),
                record.getTreatmentStage(),
                record.getTestPlateLabel(),
                record.getTestPlateHardnessHb(),
                record.getDetails(),
                disposition == null ? null : disposition.getId(),
                disposition == null ? null : disposition.getTensileTest().getTestIndex(),
                record.getCreatedAt(),
                record.getCreatedBy(),
                canDelete(username)
        );
    }

    private boolean canDelete(String username) {
        if (username == null) return false;
        return permissionService.isAdmin(username)
                || permissionService.hasPermission(username, Permission.MANAGE_HEAT_TREATMENT_LOG);
    }

    private String resolveDisplayName(String username) {
        return userRepository.findByUsername(username)
                .map(u -> (u.getFullName() != null && !u.getFullName().isBlank()) ? u.getFullName() : u.getUsername())
                .orElse(username);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
