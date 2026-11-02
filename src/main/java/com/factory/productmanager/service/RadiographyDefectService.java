package com.factory.productmanager.service;

import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.DefectPositionDto;
import com.factory.productmanager.model.dto.RadiographyDefectRequest;
import com.factory.productmanager.model.dto.RadiographyDefectResponse;
import com.factory.productmanager.model.dto.RadiographySecondDefectDto;
import com.factory.productmanager.model.entity.CorrectiveAction;
import com.factory.productmanager.model.entity.DefectPosition;
import com.factory.productmanager.model.entity.DefectStatus;
import com.factory.productmanager.model.entity.DefectTypes;
import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.RadiographyDefect;
import com.factory.productmanager.model.entity.RadiographyReportLine;
import com.factory.productmanager.repository.RadiographyDefectRepository;
import com.factory.productmanager.repository.RadiographyReportLineRepository;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * عیب‌های سطح گزارش یک «زنجیره» هستند:
 * موقعیت‌ها → عیب 1 → تایپ 1 → جوشکاری شده/نشده → اقدام اصلاحی 1 → [عیب 2 و اقدام آن، یا ریشوت → OK].
 * عیب دوم به‌صورت یک رکورد فرزند (parentDefect) ذخیره می‌شود و بیش از دو عیب ممکن نیست.
 * عیب‌های بدون reportLineId (پنل قدیمی عیوب قطعه) مثل قبل رفتار می‌کنند.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class RadiographyDefectService {

    private static final int MAX_POSITIONS = 50;

    private final RadiographyDefectRepository radiographyDefectRepository;
    private final RadiographyReportLineRepository radiographyReportLineRepository;
    private final PieceService pieceService;
    private final UserRepository userRepository;
    private final RadiographyDefectMapper defectMapper;
    private final RadiographyWorkflowGate workflowGate;

    private String resolveDisplayName(String username) {
        return userRepository.findByUsername(username)
                .map(u -> (u.getFullName() != null && !u.getFullName().isBlank()) ? u.getFullName() : u.getUsername())
                .orElse(username);
    }

    public RadiographyDefectResponse createDefect(UUID pieceId, RadiographyDefectRequest request, String recordedByUsername) {
        Piece piece = pieceService.findPieceOrThrow(pieceId);
        assertPieceEditable(piece);

        RadiographyDefect defect = new RadiographyDefect();
        defect.setPiece(piece);
        defect.setWeldingConfirmed(false);
        defect.setRecordedBy(resolveDisplayName(recordedByUsername));

        if (request.getReportLineId() != null) {
            RadiographyReportLine line = radiographyReportLineRepository.findById(request.getReportLineId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "سطر گزارش رادیوگرافی با شناسه " + request.getReportLineId() + " پیدا نشد"));
            if (!line.getReport().getPiece().getId().equals(pieceId)) {
                throw new IllegalStateException("این سطر گزارش متعلق به قطعه‌ی دیگری است");
            }
            defect.setReportLine(line);
            defect.setCourse(line.getTemplateLine().getCourseCode());

            applyChainFields(defect, request);
            RadiographyDefect saved = radiographyDefectRepository.save(defect);
            RadiographyDefect second = applySecondDefect(saved, request.getSecondDefect());
            return defectMapper.toResponse(saved, second);
        }

        String course = request.getCourse() == null ? "" : request.getCourse().trim();
        if (course.isEmpty()) {
            throw new IllegalArgumentException("کورس نمی‌تواند خالی باشد");
        }
        defect.setCourse(course);
        applyDefectFields(defect, request);
        return toResponse(radiographyDefectRepository.save(defect));
    }

    public RadiographyDefectResponse updateDefect(UUID id, RadiographyDefectRequest request) {
        RadiographyDefect defect = radiographyDefectRepository.findForUpdateById(id)
                .orElseThrow(() -> new ResourceNotFoundException("عیب رادیوگرافی با شناسه " + id + " پیدا نشد"));
        assertPieceEditable(defect.getPiece());
        if (defect.getParentDefect() != null) {
            throw new IllegalStateException("عیب دوم را از طریق عیب اول همان زنجیره ویرایش کنید");
        }

        if (defect.getReportLine() != null) {
            applyChainFields(defect, request);
            RadiographyDefect saved = radiographyDefectRepository.save(defect);
            RadiographyDefect second = applySecondDefect(saved, request.getSecondDefect());
            return defectMapper.toResponse(saved, second);
        }

        applyDefectFields(defect, request);
        return toResponse(radiographyDefectRepository.save(defect));
    }

    @Transactional(readOnly = true)
    public List<RadiographyDefectResponse> getDefectsByPiece(UUID pieceId) {
        pieceService.findPieceOrThrow(pieceId);
        List<RadiographyDefect> all = radiographyDefectRepository.findByPieceIdOrderByCreatedAtAsc(pieceId);

        // عیب دوم هر زنجیره داخل پاسخ عیب اول می‌آید، نه به‌عنوان یک ردیف جدا
        Map<UUID, RadiographyDefect> secondByParent = new HashMap<>();
        for (RadiographyDefect d : all) {
            if (d.getParentDefect() != null) {
                secondByParent.put(d.getParentDefect().getId(), d);
            }
        }
        return all.stream()
                .filter(d -> d.getParentDefect() == null)
                .map(d -> defectMapper.toResponse(d, secondByParent.get(d.getId())))
                .toList();
    }

    public RadiographyDefectResponse confirmWelding(UUID id) {
        RadiographyDefect defect = radiographyDefectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("عیب رادیوگرافی با شناسه " + id + " پیدا نشد"));

        workflowGate.assertReportEditable(defect.getPiece().getId());

        if (defect.getStatus() != DefectStatus.NEEDS_WELDING) {
            throw new IllegalStateException("فقط عیوب با وضعیت «نیاز به جوش» قابل تایید جوشکاری هستند");
        }
        if (defect.isWeldingConfirmed()) {
            throw new IllegalStateException("جوشکاری این عیب قبلاً تایید شده است");
        }

        defect.setWeldingConfirmed(true);
        return toResponse(radiographyDefectRepository.save(defect));
    }

    public void deleteDefect(UUID id) {
        RadiographyDefect defect = radiographyDefectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("عیب رادیوگرافی با شناسه " + id + " پیدا نشد"));
        workflowGate.assertReportEditable(defect.getPiece().getId());

        if (defect.getParentDefect() != null) {
            throw new IllegalStateException("عیب دوم همراه عیب اول حذف می‌شود؛ زنجیره را ویرایش کنید");
        }
        // اول عیب دوم، چون کلید خارجی آن به عیب اول اشاره می‌کند
        radiographyDefectRepository.findByParentDefectId(defect.getId()).ifPresent(second -> {
            radiographyDefectRepository.delete(second);
            radiographyDefectRepository.flush();
        });
        radiographyDefectRepository.delete(defect);
    }

    /* ===========================
       زنجیره‌ی گزارش (عیب 1 + عیب 2 اختیاری)
       =========================== */

    private void applyChainFields(RadiographyDefect defect, RadiographyDefectRequest request) {
        if (Boolean.TRUE.equals(request.getReshootOk()) && request.getSecondDefect() != null) {
            throw new IllegalArgumentException("فقط یکی از «عیب دوم» یا «ریشوت → OK» قابل ثبت است");
        }
        List<DefectPosition> positions = normalizePositions(request.getPositions());

        applyType(defect, request.getDefectType(), request.getDefectTypeCustom());

        // تایپ اختیاری است؛ ستون type_code خالی را به‌صورت رشته‌ی خالی نگه می‌دارد
        String defectClass = firstNonBlank(request.getDefectClass(), request.getTypeCode());
        defect.setDefectClass(defectClass == null ? "" : defectClass);

        // «جوشکاری شده» تیک‌دار است؛ نبودن آن یعنی نیاز به جوش نیست
        boolean welded = Boolean.TRUE.equals(request.getWelded());
        defect.setWelded(welded);

        CorrectiveAction action = CorrectiveAction.optional(request.getCorrectiveAction(), "اقدام اصلاحی اول");
        defect.setCorrectiveAction(action == null ? null : action.name());
        defect.setReshootOk(Boolean.TRUE.equals(request.getReshootOk()));

        defect.getPositions().clear();
        defect.getPositions().addAll(positions);
        DefectPosition first = positions.isEmpty() ? null : positions.get(0);
        defect.setStartCm(first == null ? null : first.getStartCm());
        defect.setEndCm(first == null ? null : first.getEndCm());
        defect.setLocationCm(first == null ? null : first.getStartCm());

        defect.setDescription(trimToNull(request.getDescription()));

        // «جوشکاری شده» فقط توصیفی است. تنها استثنا: رکورد قدیمیِ «نیاز به جوش» که هنوز
        // جوشکاری‌شده علامت خورده است، وضعیتش را برای ایستگاه جوش حفظ می‌کند.
        boolean keepNeedsWelding = defect.getStatus() == DefectStatus.NEEDS_WELDING && welded;
        defect.setStatus(keepNeedsWelding ? DefectStatus.NEEDS_WELDING : DefectStatus.APPROVED);
    }

    /**
     * عیب دوم را می‌سازد/به‌روز می‌کند؛ اگر dto خالی باشد عیب دوم موجود حذف می‌شود.
     * عیب دوم جوشکاری و موقعیت ندارد و هرگز فرزندی نخواهد داشت.
     */
    private RadiographyDefect applySecondDefect(RadiographyDefect parent, RadiographySecondDefectDto dto) {
        RadiographyDefect existing = radiographyDefectRepository.findByParentDefectId(parent.getId()).orElse(null);

        if (dto == null) {
            if (existing != null) {
                radiographyDefectRepository.delete(existing);
                radiographyDefectRepository.flush();
            }
            return null;
        }

        // فقط نام عیب دوم لازم است؛ تایپ و اقدام اصلاحی اختیاری‌اند
        String defectClass = trimToNull(dto.getDefectClass());
        if (trimToNull(dto.getDefectType()) == null) {
            throw new IllegalArgumentException("نام عیب دوم را وارد کنید");
        }
        CorrectiveAction action = CorrectiveAction.optional(dto.getCorrectiveAction(), "اقدام اصلاحی دوم");

        RadiographyDefect second = existing;
        if (second == null) {
            second = new RadiographyDefect();
            second.setPiece(parent.getPiece());
            second.setParentDefect(parent);
            second.setRecordedBy(parent.getRecordedBy());
            second.setWeldingConfirmed(false);
        }
        second.setReportLine(parent.getReportLine());
        second.setCourse(parent.getCourse());
        applyType(second, dto.getDefectType(), dto.getDefectTypeCustom());
        second.setDefectClass(defectClass == null ? "" : defectClass);
        second.setCorrectiveAction(action == null ? null : action.name());
        second.setWelded(false);
        second.setStatus(DefectStatus.APPROVED);
        return radiographyDefectRepository.save(second);
    }

    private List<DefectPosition> normalizePositions(List<DefectPositionDto> requested) {
        // موقعیت اختیاری است؛ ولی هر موقعیتی که فرستاده شود باید معتبر باشد
        if (requested == null || requested.isEmpty()) {
            return new ArrayList<>();
        }
        if (requested.size() > MAX_POSITIONS) {
            throw new IllegalArgumentException("حداکثر " + MAX_POSITIONS + " موقعیت برای یک عیب مجاز است");
        }
        List<DefectPosition> result = new ArrayList<>();
        Set<List<String>> seen = new HashSet<>();
        for (DefectPositionDto p : requested) {
            if (p == null || trimToNull(p.getStartCm()) == null) {
                throw new IllegalArgumentException("شروع هر موقعیت را وارد کنید");
            }
            String start = trimToNull(p.getStartCm());
            String end = trimToNull(p.getEndCm());
            if (end == null) end = start;
            if (start.length() > 255 || end.length() > 255) {
                throw new IllegalArgumentException("هر موقعیت حداکثر ۲۵۵ کاراکتر است");
            }
            if (!seen.add(List.of(start, end))) {
                throw new IllegalArgumentException("موقعیت تکراری است: " + (start.equals(end) ? "(" + start + ")" : "(" + start + "-" + end + ")"));
            }
            result.add(new DefectPosition(start, end));
        }
        return result;
    }

    /* ===========================
       عیب‌های بدون سطر گزارش (پنل قدیمی عیوب قطعه)
       =========================== */

    private void applyDefectFields(RadiographyDefect defect, RadiographyDefectRequest request) {
        if (Boolean.TRUE.equals(request.getReshootOk()) || request.getSecondDefect() != null) {
            throw new IllegalArgumentException("نتیجه بررسی مجدد فقط برای عیب داخل گزارش قابل ثبت است");
        }
        applyType(defect, request.getDefectType(), request.getDefectTypeCustom());

        String defectClass = firstNonBlank(request.getDefectClass(), request.getTypeCode());
        defect.setDefectClass(defectClass == null ? "" : defectClass);

        String start = firstNonBlank(request.getStartCm(), request.getLocationCm());
        String end = trimToNull(request.getEndCm());
        if (end == null) end = start;
        defect.setStartCm(start);
        defect.setEndCm(end);
        defect.setLocationCm(start);
        defect.setDescription(trimToNull(request.getDescription()));

        if (request.getLeadsToWelding() != null) {
            defect.setStatus(Boolean.TRUE.equals(request.getLeadsToWelding())
                    ? DefectStatus.NEEDS_WELDING
                    : DefectStatus.APPROVED);
        } else if (request.getStatus() != null) {
            defect.setStatus(request.getStatus());
        } else if (defect.getStatus() == null) {
            defect.setStatus(DefectStatus.APPROVED);
        }
        // برای این عیب‌ها «جوشکاری شده» همان نیاز به جوش است
        defect.setWelded(defect.getStatus() == DefectStatus.NEEDS_WELDING);

        defect.getPositions().clear();
        if (start != null) {
            defect.getPositions().add(new DefectPosition(start, end == null ? start : end));
        }
    }

    /** نوع عیب (یا «سایر» با شرح) را روی هر رکوردی که عیب نگه می‌دارد اعمال می‌کند */
    private void applyType(RadiographyDefect target, String rawType, String rawCustom) {
        String type = DefectTypes.requireStoredType(rawType, rawCustom);
        target.setDefectType(type);
        if (DefectTypes.OTHER.equals(type)) {
            String custom = DefectTypes.storedCustom(type, rawCustom);
            if (custom == null) {
                throw new IllegalArgumentException("برای گزینهٔ سایر، نوع عیب را بنویسید");
            }
            target.setDefectTypeCustom(custom);
        } else {
            target.setDefectTypeCustom(null);
        }
    }

    private RadiographyDefectResponse toResponse(RadiographyDefect defect) {
        RadiographyDefect second = defect.getId() == null
                ? null
                : radiographyDefectRepository.findByParentDefectId(defect.getId()).orElse(null);
        return defectMapper.toResponse(defect, second);
    }

    private void assertPieceEditable(Piece piece) {
        workflowGate.assertReportEditable(piece.getId());
        if (piece.getArchivedAt() != null) {
            throw new IllegalStateException("قطعه بایگانی‌شده قابل ویرایش نیست");
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        String first = trimToNull(primary);
        if (first != null) {
            return first;
        }
        return trimToNull(fallback);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
