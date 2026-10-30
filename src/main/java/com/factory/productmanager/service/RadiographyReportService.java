package com.factory.productmanager.service;

import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.RadiographyDefectResponse;
import com.factory.productmanager.model.dto.RadiographyReportLineRequest;
import com.factory.productmanager.model.dto.RadiographyReportLineResponse;
import com.factory.productmanager.model.dto.RadiographyReportResponse;
import com.factory.productmanager.model.dto.RadiographyTemplateLineRequest;
import com.factory.productmanager.model.dto.RadiographyTemplateLineResponse;
import com.factory.productmanager.model.dto.RadiographyTemplateRequest;
import com.factory.productmanager.model.dto.RadiographyTemplateResponse;
import com.factory.productmanager.model.dto.SaveRadiographyReportRequest;
import com.factory.productmanager.model.entity.DefectStatus;
import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.PieceDefinition;
import com.factory.productmanager.model.entity.RadiographyDefect;
import com.factory.productmanager.model.entity.RadiographyReport;
import com.factory.productmanager.model.entity.RadiographyReportLine;
import com.factory.productmanager.model.entity.RadiographyReportTemplate;
import com.factory.productmanager.model.entity.RadiographyReportTemplateLine;
import com.factory.productmanager.repository.PieceDefinitionRepository;
import com.factory.productmanager.repository.RadiographyDefectRepository;
import com.factory.productmanager.repository.RadiographyReportRepository;
import com.factory.productmanager.repository.RadiographyReportTemplateRepository;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Set;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * قالب گزارش رادیوگرافی روی ردیف قطعه ساخته می‌شود و بعد از ذخیره قفل است.
 * گزارش هر قطعه برای هر قالب حداکثر یکی است و همان ویرایش می‌شود.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class RadiographyReportService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_IN_INTERPRETATION = "IN_INTERPRETATION";
    public static final String STATUS_COMPLETED = "COMPLETED";
    /** گردش‌کار تفسیر: مفسر گزارش را تأیید کرده و منتظر تأیید مدیر است (فقط توسط گردش‌کار تنظیم می‌شود) */
    public static final String STATUS_PENDING_MANAGER_APPROVAL = "PENDING_MANAGER_APPROVAL";
    /** گردش‌کار تفسیر: مدیر گزارش را رد کرده و باید دوباره تفسیر شود (فقط توسط گردش‌کار تنظیم می‌شود) */
    public static final String STATUS_REINTERPRETATION_REQUIRED = "REINTERPRETATION_REQUIRED";
    private static final Set<String> STATUSES = Set.of(
            STATUS_DRAFT, STATUS_IN_INTERPRETATION, STATUS_COMPLETED);
    private static final Set<String> RESULTS = Set.of("RS", "RT", "RP", "RJ", "A.C.C");

    private final RadiographyReportTemplateRepository templateRepository;
    private final RadiographyReportRepository reportRepository;
    private final RadiographyDefectRepository defectRepository;
    private final PieceDefinitionRepository pieceDefinitionRepository;
    private final PieceService pieceService;
    private final UserRepository userRepository;
    private final RadiographyWorkflowGate workflowGate;
    private final RadiographyDefectMapper defectMapper;

    private String resolveDisplayName(String username) {
        return userRepository.findByUsername(username)
                .map(u -> (u.getFullName() != null && !u.getFullName().isBlank()) ? u.getFullName() : u.getUsername())
                .orElse(username);
    }

    @Transactional(readOnly = true)
    public List<RadiographyTemplateResponse> getTemplates(UUID pieceDefinitionId) {
        findDefinitionOrThrow(pieceDefinitionId);
        return templateRepository.findByPieceDefinitionIdWithLines(pieceDefinitionId).stream()
                .map(this::toTemplateResponse)
                .toList();
    }

    public RadiographyTemplateResponse createTemplate(
            UUID pieceDefinitionId,
            RadiographyTemplateRequest request,
            String username
    ) {
        PieceDefinition definition = findDefinitionOrThrow(pieceDefinitionId);
        String name = request.getName().trim();

        if (templateRepository.existsByPieceDefinitionIdAndNameIgnoreCase(pieceDefinitionId, name)) {
            throw new IllegalStateException("قالبی با نام «" + name + "» برای این ردیف قطعه وجود دارد");
        }

        RadiographyReportTemplate template = new RadiographyReportTemplate();
        template.setPieceDefinition(definition);
        template.setName(name);
        template.setCreatedBy(resolveDisplayName(username));
        applyTemplateLines(template, request.getLines());

        return toTemplateResponse(templateRepository.save(template));
    }

    /**
     * افزودن کورس جدید همیشه مجاز است. ویرایش/حذف/جابه‌جایی کورس‌های موجود و
     * فقط در صورتی که روی این قالب هنوز گزارشی ثبت نشده باشد.
     */
    public RadiographyTemplateResponse updateTemplate(UUID templateId, RadiographyTemplateRequest request) {
        RadiographyReportTemplate template = findTemplateOrThrow(templateId);
        long reportCount = reportRepository.countByTemplateId(templateId);
        String name = request.getName().trim();
        UUID definitionId = template.getPieceDefinition().getId();

        if (!template.getName().equalsIgnoreCase(name)
                && templateRepository.existsByPieceDefinitionIdAndNameIgnoreCase(definitionId, name)) {
            throw new IllegalStateException("قالبی با نام «" + name + "» برای این ردیف قطعه وجود دارد");
        }

        List<RadiographyTemplateLineRequest> requested = request.getLines();
        Set<String> seen = new HashSet<>();
        Set<UUID> keptIds = new HashSet<>();
        for (RadiographyTemplateLineRequest req : requested) {
            if (!seen.add(req.getCourseCode().trim().toUpperCase())) {
                throw new IllegalStateException("LINE NO «" + req.getCourseCode().trim() + "» در این قالب تکراری است");
            }
            if (req.getId() != null) {
                keptIds.add(req.getId());
            }
        }

        Map<UUID, RadiographyReportTemplateLine> existing = new HashMap<>();
        for (RadiographyReportTemplateLine line : template.getLines()) {
            existing.put(line.getId(), line);
        }
        for (UUID keptId : keptIds) {
            if (!existing.containsKey(keptId)) {
                throw new IllegalStateException("کورس انتخاب‌شده متعلق به این قالب نیست");
            }
        }

        boolean removesAny = existing.keySet().stream().anyMatch(id -> !keptIds.contains(id));
        if (reportCount > 0 && removesAny) {
            throw new IllegalStateException(
                    "این قالب در " + reportCount + " گزارش استفاده شده است و کورس‌های قبلی آن قابل حذف نیستند");
        }
        if (reportCount > 0) {
            for (RadiographyTemplateLineRequest req : requested) {
                if (req.getId() == null) continue;
                RadiographyReportTemplateLine line = existing.get(req.getId());
                if (!sameLineValues(line, req)) {
                    throw new IllegalStateException(
                            "این قالب در گزارش استفاده شده است؛ فقط افزودن کورس جدید ممکن است");
                }
            }
        }

        template.setName(name);
        if (removesAny) {
            template.getLines().removeIf(line -> !keptIds.contains(line.getId()));
            templateRepository.flush();
        }

        int order = 1;
        for (RadiographyTemplateLineRequest req : requested) {
            RadiographyReportTemplateLine line = req.getId() != null ? existing.get(req.getId()) : null;
            if (line == null) {
                line = new RadiographyReportTemplateLine();
                line.setTemplate(template);
                template.getLines().add(line);
            }
            line.setSortOrder(order++);
            line.setCourseCode(req.getCourseCode().trim());
            line.setImageCount(trimToNull(req.getImageCount()));
            line.setIqiAbs(trimToNull(req.getIqiAbs()));
            line.setAccCriteria(trimToNull(req.getAccCriteria()));
            line.setTh(trimToNull(req.getTh()));
        }

        return toTemplateResponse(templateRepository.saveAndFlush(template));
    }

    private static boolean sameLineValues(RadiographyReportTemplateLine line, RadiographyTemplateLineRequest req) {
        return java.util.Objects.equals(line.getCourseCode(), req.getCourseCode().trim())
                && java.util.Objects.equals(line.getImageCount(), trimToNull(req.getImageCount()))
                && java.util.Objects.equals(line.getIqiAbs(), trimToNull(req.getIqiAbs()))
                && java.util.Objects.equals(line.getAccCriteria(), trimToNull(req.getAccCriteria()))
                && java.util.Objects.equals(line.getTh(), trimToNull(req.getTh()));
    }

    public void deleteTemplate(UUID templateId) {
        RadiographyReportTemplate template = findTemplateOrThrow(templateId);

        long reportCount = reportRepository.countByTemplateId(templateId);
        if (reportCount > 0) {
            throw new IllegalStateException(
                    "این قالب در " + reportCount + " گزارش استفاده شده است و قابل حذف نیست");
        }

        templateRepository.delete(template);
    }

    private void applyTemplateLines(RadiographyReportTemplate template, List<RadiographyTemplateLineRequest> requested) {
        Set<String> seen = new HashSet<>();
        int order = 1;
        for (RadiographyTemplateLineRequest req : requested) {
            String courseCode = req.getCourseCode().trim();
            if (!seen.add(courseCode.toUpperCase())) {
                throw new IllegalStateException("LINE NO «" + courseCode + "» در این قالب تکراری است");
            }
            RadiographyReportTemplateLine line = new RadiographyReportTemplateLine();
            line.setTemplate(template);
            line.setSortOrder(order++);
            line.setCourseCode(courseCode);
            line.setImageCount(trimToNull(req.getImageCount()));
            line.setIqiAbs(trimToNull(req.getIqiAbs()));
            line.setAccCriteria(trimToNull(req.getAccCriteria()));
            line.setTh(trimToNull(req.getTh()));
            template.getLines().add(line);
        }
    }

    /**
     * قالب‌های ردیف این قطعه به‌همراه گزارش‌های صادرشده.
     * اگر بعد از صدور گزارش لاین جدیدی به قالب اضافه شده باشد (نباید بشود)،
     * سطر خالی متناظر ساخته می‌شود تا جدول کامل بماند.
     */
    public List<RadiographyReportResponse> getReportsByPiece(UUID pieceId) {
        Piece piece = pieceService.findPieceOrThrow(pieceId);
        Map<UUID, List<RadiographyDefect>> defectsByLine = defectsByReportLine(pieceId);

        List<RadiographyReport> reports = reportRepository.findByPieceIdOrderByCreatedAtAsc(piece.getId());
        reports.forEach(this::syncMissingLines);
        reportRepository.flush();
        return reports.stream()
                .map(report -> toReportResponse(report, defectsByLine))
                .toList();
    }

    public RadiographyReportResponse saveReport(UUID pieceId, SaveRadiographyReportRequest request, String username) {
        Piece piece = pieceService.findPieceOrThrow(pieceId);
        workflowGate.assertReportEditable(piece.getId());
        RadiographyReportTemplate template = findTemplateOrThrow(request.getTemplateId());

        if (!template.getPieceDefinition().getId().equals(piece.getPieceDefinition().getId())) {
            throw new IllegalStateException("این قالب متعلق به ردیف قطعه‌ی دیگری است");
        }

        RadiographyReport report = reportRepository
                .findByPieceIdAndTemplateId(piece.getId(), template.getId())
                .orElseGet(() -> {
                    RadiographyReport created = new RadiographyReport();
                    created.setPiece(piece);
                    created.setTemplate(template);
                    created.setCreatedBy(resolveDisplayName(username));
                    created.setStatus(STATUS_DRAFT);
                    return created;
                });

        if (report.getPartName() == null || report.getPartName().isBlank()) {
            report.setPartName(piece.getPieceDefinition().getPieceName());
        }
        if (report.getProjectCode() == null || report.getProjectCode().isBlank()) {
            report.setProjectCode(piece.getPieceDefinition().getProject().getCode());
        }
        report.setReportDate(request.getReportDate());
        report.setReportNumber(trimToNull(request.getReportNumber()));
        report.setRequestNumber(trimToNull(request.getRequestNumber()));
        report.setReferenceStandard(trimToNull(request.getReferenceStandard()));
        syncMissingLines(report);

        List<RadiographyReportLineRequest> requestedLines =
                request.getLines() == null ? List.of() : request.getLines();
        Map<UUID, RadiographyReportLineRequest> byTemplateLine = requestedLines.stream()
                .collect(java.util.stream.Collectors.toMap(
                        RadiographyReportLineRequest::getTemplateLineId, l -> l, (a, b) -> b));

        for (RadiographyReportLine line : report.getLines()) {
            RadiographyReportLineRequest req = byTemplateLine.get(line.getTemplateLine().getId());
            if (req == null) continue;
            line.setIqiReal(trimToNull(req.getIqiReal()));
            line.setResult(normalizeResult(req.getResult()));
        }
        report.setStatus(resolveStatus(report, request.getStatus()));

        if (report.getId() != null) {
            report.setUpdatedAt(LocalDateTime.now());
            report.setUpdatedBy(resolveDisplayName(username));
        }

        // flush تا سطرهای تازه‌ساخته‌شده (کورس‌های اضافه‌شده بعد از صدور گزارش) شناسه بگیرند
        RadiographyReport saved = reportRepository.saveAndFlush(report);
        return toReportResponse(saved, defectsByReportLine(pieceId));
    }

    public void deleteReport(UUID reportId) {
        RadiographyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("گزارش رادیوگرافی با شناسه " + reportId + " پیدا نشد"));
        workflowGate.assertReportEditable(report.getPiece().getId());
        reportRepository.delete(report);
    }

    private void syncMissingLines(RadiographyReport report) {
        Set<UUID> covered = report.getLines().stream()
                .map(line -> line.getTemplateLine().getId())
                .collect(java.util.stream.Collectors.toSet());

        for (RadiographyReportTemplateLine templateLine : report.getTemplate().getLines()) {
            if (covered.contains(templateLine.getId())) continue;
            RadiographyReportLine line = new RadiographyReportLine();
            line.setReport(report);
            line.setTemplateLine(templateLine);
            report.getLines().add(line);
        }
    }

    private Map<UUID, List<RadiographyDefect>> defectsByReportLine(UUID pieceId) {
        Map<UUID, List<RadiographyDefect>> grouped = new HashMap<>();
        for (RadiographyDefect defect : defectRepository.findByPieceIdOrderByCreatedAtAsc(pieceId)) {
            if (defect.getReportLine() == null) continue;
            // عیب دوم هر زنجیره داخل پاسخ عیب اول می‌آید، نه به‌عنوان یک عیب مستقل
            if (defect.getParentDefect() != null) continue;
            grouped.computeIfAbsent(defect.getReportLine().getId(), k -> new ArrayList<>()).add(defect);
        }
        return grouped;
    }

    private RadiographyTemplateResponse toTemplateResponse(RadiographyReportTemplate template) {
        List<RadiographyTemplateLineResponse> lines = template.getLines().stream()
                .sorted(Comparator.comparingInt(RadiographyReportTemplateLine::getSortOrder))
                .map(this::toTemplateLineResponse)
                .toList();

        return new RadiographyTemplateResponse(
                template.getId(),
                template.getPieceDefinition().getId(),
                template.getName(),
                template.getCreatedAt(),
                template.getCreatedBy(),
                lines,
                reportRepository.countByTemplateId(template.getId())
        );
    }

    private RadiographyTemplateLineResponse toTemplateLineResponse(RadiographyReportTemplateLine line) {
        return new RadiographyTemplateLineResponse(
                line.getId(),
                line.getSortOrder(),
                line.getCourseCode(),
                line.getImageCount(),
                line.getIqiAbs(),
                line.getAccCriteria(),
                line.getTh()
        );
    }

    private RadiographyReportResponse toReportResponse(
            RadiographyReport report,
            Map<UUID, List<RadiographyDefect>> defectsByLine
    ) {
        List<RadiographyReportLineResponse> lines = report.getLines().stream()
                .sorted(Comparator.comparingInt(line -> line.getTemplateLine().getSortOrder()))
                .map(line -> toReportLineResponse(line, defectsByLine.getOrDefault(line.getId(), List.of())))
                .toList();

        return new RadiographyReportResponse(
                report.getId(),
                report.getPiece().getId(),
                report.getTemplate().getId(),
                report.getTemplate().getName(),
                report.getPartName(),
                report.getProjectCode(),
                report.getReportDate(),
                report.getReportNumber(),
                report.getRequestNumber(),
                report.getReferenceStandard(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getCreatedBy(),
                report.getUpdatedAt(),
                report.getUpdatedBy(),
                lines
        );
    }

    private RadiographyReportLineResponse toReportLineResponse(
            RadiographyReportLine line,
            List<RadiographyDefect> defects
    ) {
        RadiographyReportTemplateLine templateLine = line.getTemplateLine();
        return new RadiographyReportLineResponse(
                line.getId(),
                templateLine.getId(),
                templateLine.getSortOrder(),
                templateLine.getCourseCode(),
                templateLine.getImageCount(),
                templateLine.getIqiAbs(),
                templateLine.getAccCriteria(),
                templateLine.getTh(),
                line.getIqiReal(),
                line.getResult(),
                defects.stream().map(this::toDefectResponse).toList()
        );
    }

    private RadiographyDefectResponse toDefectResponse(RadiographyDefect defect) {
        RadiographyDefect second = defectRepository.findByParentDefectId(defect.getId()).orElse(null);
        return defectMapper.toResponse(defect, second);
    }

    private String resolveStatus(RadiographyReport report, String requested) {
        String explicit = trimToNull(requested);
        if (explicit != null) {
            if (!STATUSES.contains(explicit)) {
                throw new IllegalArgumentException("وضعیت گزارش باید DRAFT، IN_INTERPRETATION یا COMPLETED باشد");
            }
            return explicit;
        }
        if (STATUS_COMPLETED.equals(report.getStatus())) {
            return STATUS_COMPLETED;
        }
        boolean interpreted = report.getLines().stream().anyMatch(line ->
                (line.getIqiReal() != null && !line.getIqiReal().isBlank())
                        || (line.getResult() != null && !line.getResult().isBlank()));
        if (!interpreted && report.getId() != null) {
            interpreted = defectRepository.countByReportId(report.getId()) > 0;
        }
        return interpreted ? STATUS_IN_INTERPRETATION : STATUS_DRAFT;
    }

    private static String normalizeResult(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        if ("ACC".equalsIgnoreCase(value) || "A.C.C".equalsIgnoreCase(value)) {
            return "A.C.C";
        }
        for (String allowed : RESULTS) {
            if (allowed.equalsIgnoreCase(value)) {
                return allowed;
            }
        }
        throw new IllegalArgumentException("نتیجه باید یکی از RS، RT، RP، RJ یا A.C.C باشد");
    }

    private PieceDefinition findDefinitionOrThrow(UUID id) {
        return pieceDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("تعریف قطعه با شناسه " + id + " پیدا نشد"));
    }

    private RadiographyReportTemplate findTemplateOrThrow(UUID id) {
        return templateRepository.findByIdWithLines(id)
                .orElseThrow(() -> new ResourceNotFoundException("قالب گزارش رادیوگرافی با شناسه " + id + " پیدا نشد"));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
