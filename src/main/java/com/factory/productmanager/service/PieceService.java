package com.factory.productmanager.service;

import com.factory.productmanager.exception.DuplicateResourceException;
import com.factory.productmanager.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.PieceDefinition;
import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.PieceStageHistory;
import com.factory.productmanager.model.entity.PieceSubStageHistory;
import com.factory.productmanager.model.entity.DefectStatus;
import com.factory.productmanager.model.entity.DefectTypes;
import com.factory.productmanager.model.entity.HeatTreatmentSubStage;
import com.factory.productmanager.model.entity.PieceStageProcessDate;
import com.factory.productmanager.model.entity.RadiographyDefect;
import com.factory.productmanager.model.entity.RadiographySubStage;
import com.factory.productmanager.model.entity.RoughMachiningSubStage;
import com.factory.productmanager.model.entity.SubStageKind;
import com.factory.productmanager.repository.PieceRepository;
import com.factory.productmanager.repository.PieceSearchTerm;
import com.factory.productmanager.repository.PieceStageHistoryRepository;
import com.factory.productmanager.repository.PieceStageProcessDateRepository;
import com.factory.productmanager.repository.PieceSubStageHistoryRepository;
import com.factory.productmanager.repository.PieceSubStageProcessDateRepository;
import com.factory.productmanager.model.entity.PieceSubStageProcessDate;
import com.factory.productmanager.repository.PieceWeldingDispatchRepository;
import com.factory.productmanager.model.entity.PieceWeldingDispatch;
import com.factory.productmanager.repository.RadiographyDefectRepository;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Transactional
@RequiredArgsConstructor
public class PieceService {
    private final PieceRepository pieceRepository;
    private final PieceStageHistoryRepository pieceStageHistoryRepository;
    private final PieceSubStageHistoryRepository pieceSubStageHistoryRepository;
    private final PieceWeldingDispatchRepository pieceWeldingDispatchRepository;
    private final ScrapService scrapService;
    private final PieceDefinitionService pieceDefinitionService;
    private final ProjectService projectService;
    private final UserRepository userRepository;
    private final RadiographyDefectRepository radiographyDefectRepository;
    private final PieceStageProcessDateRepository pieceStageProcessDateRepository;
    private final PieceSubStageProcessDateRepository pieceSubStageProcessDateRepository;
    private final PermissionService permissionService;
    private final StageTransitionPolicy stageTransitionPolicy;
    private final RadiographyWorkflowGate radiographyWorkflowGate;
    private final TensileTrackingService tensileTrackingService;
    private final MeltPassportGate meltPassportGate;

    /**
     * نام قابل‌نمایش کاربر (نام و نام خانوادگی؛ در صورت نبود، نام کاربری) را برای ثبت در تاریخچه برمی‌گرداند.
     */
    private String resolveDisplayName(String username) {
        return userRepository.findByUsername(username)
                .map(u -> (u.getFullName() != null && !u.getFullName().isBlank()) ? u.getFullName() : u.getUsername())
                .orElse(username);
    }
    public PieceResponse createPiece(PieceRequest request) {
        PieceDefinition definition = pieceDefinitionService.findDefinitionOrThrow(request.getPieceDefinitionId());

        boolean exists = pieceRepository.existsByPieceDefinitionIdAndPieceNumber(
                request.getPieceDefinitionId(),
                request.getPieceNumber()
        );

        if (exists) {
            throw new DuplicateResourceException(
                    "قطعه‌ای با شماره " + request.getPieceNumber() +
                            " برای این تعریف قطعه از قبل وجود دارد"
            );
        }

        Piece piece = new Piece();
        piece.setPieceDefinition(definition);
        piece.setPieceNumber(request.getPieceNumber());
        piece.setCurrentStage(PieceStage.NOT_STARTED);
        Piece saved = pieceRepository.save(piece);
        // قطعه‌ی تازه‌ساخته‌شده قطعاً هنوز عیبی ندارد و هنوز هیچ تغییر وضعیتی نداشته
        return toResponse(saved, 0L, saved.getCreatedAt());
    }
    /**
     * ساخت دسته‌ای (Bulk) چند قطعه با شماره‌های پیاپی برای یک تعریف قطعه‌ی مشخص.
     * اگر حتی یکی از شماره‌های بازه‌ی درخواستی از قبل وجود داشته باشد، کل عملیات رد می‌شود
     * (تا از ساخت نیمه‌کاره و سردرگمی جلوگیری شود).
     */
    public List<PieceResponse> bulkCreatePieces(BulkCreatePieceRequest request) {
        PieceDefinition definition = pieceDefinitionService.findDefinitionOrThrow(request.getPieceDefinitionId());

        int start = request.getStartNumber();
        int quantity = request.getQuantity();
        int end = start + quantity - 1;

        Set<Integer> existingNumbers = new HashSet<>(
                pieceRepository.findPieceNumbersByPieceDefinitionId(request.getPieceDefinitionId())
        );

        List<Integer> conflicts = IntStream.rangeClosed(start, end)
                .filter(existingNumbers::contains)
                .boxed()
                .toList();

        if (!conflicts.isEmpty()) {
            String conflictList = conflicts.stream()
                    .limit(10)
                    .map(String::valueOf)
                    .collect(Collectors.joining("، "));
            String suffix = conflicts.size() > 10 ? " و " + (conflicts.size() - 10) + " مورد دیگر" : "";
            throw new DuplicateResourceException(
                    "شماره‌های " + conflictList + suffix + " از قبل برای این تعریف قطعه وجود دارند"
            );
        }

        List<Piece> newPieces = new ArrayList<>();
        for (int number = start; number <= end; number++) {
            Piece piece = new Piece();
            piece.setPieceDefinition(definition);
            piece.setPieceNumber(number);
            piece.setCurrentStage(PieceStage.NOT_STARTED);
            newPieces.add(piece);
        }

        return pieceRepository.saveAll(newPieces)
                .stream()
                .sorted((a, b) -> Integer.compare(a.getPieceNumber(), b.getPieceNumber()))
                // قطعات تازه‌ساخته‌شده قطعاً هنوز عیبی ندارند و هنوز هیچ تغییر وضعیتی نداشته‌اند
                .map(p -> toResponse(p, 0L, p.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PieceResponse> getPiecesByDefinition(UUID pieceDefinitionId, boolean archived) {
        pieceDefinitionService.findDefinitionOrThrow(pieceDefinitionId);

        List<Piece> pieces = archived
                ? pieceRepository.findByPieceDefinitionIdAndArchivedAtIsNotNull(pieceDefinitionId)
                : pieceRepository.findByPieceDefinitionIdAndArchivedAtIsNull(pieceDefinitionId);
        Map<UUID, Long> defectsCounts = defectsCountMapFor(pieces);
        Map<UUID, LocalDateTime> stageEnteredAtMap = stageEnteredAtMapFor(pieces);

        return pieces.stream()
                .map(p -> toResponse(
                        p,
                        defectsCounts.getOrDefault(p.getId(), 0L),
                        stageEnteredAtMap.getOrDefault(p.getId(), p.getCreatedAt())
                ))
                .toList();
    }
    @Transactional(readOnly = true)
    public PieceResponse getPieceById(UUID id) {
        Piece piece = findPieceOrThrow(id);
        return toResponse(piece);
    }

    /**
     * پیدا کردن قطعه با الگوی «کدپروژه-شمارهردیف-شمارهقطعه»، مثلاً 498-2-54.
     * اگر کد پروژه خط تیره داشته باشد، از سمت راست جدا می‌شود.
     */
    @Transactional(readOnly = true)
    public PieceResponse findByRef(String code) {
        PieceRef ref = parsePieceRef(code);
        Piece piece = pieceRepository
                .findByProjectCodeAndRowNumAndPieceNumber(ref.projectCode(), ref.rowNum(), ref.pieceNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "قطعه‌ای با کد «" + code.trim() + "» پیدا نشد"
                ));
        if (piece.getArchivedAt() != null) {
            throw new IllegalStateException("این قطعه بایگانی شده است");
        }
        return toResponse(piece);
    }

    private static PieceRef parsePieceRef(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("کد قطعه را به شکل کدپروژه-ردیف-شماره وارد کنید");
        }
        String trimmed = code.trim();
        int lastDash = trimmed.lastIndexOf('-');
        int secondDash = lastDash <= 0 ? -1 : trimmed.lastIndexOf('-', lastDash - 1);
        if (lastDash <= 0 || secondDash <= 0) {
            throw new IllegalArgumentException("کد قطعه باید به شکل کدپروژه-ردیف-شماره باشد، مثلاً 498-2-54");
        }
        String projectCode = trimmed.substring(0, secondDash).trim();
        String rowPart = trimmed.substring(secondDash + 1, lastDash).trim();
        String piecePart = trimmed.substring(lastDash + 1).trim();
        try {
            int rowNum = Integer.parseInt(rowPart);
            int pieceNumber = Integer.parseInt(piecePart);
            if (projectCode.isEmpty()) {
                throw new IllegalArgumentException("کد پروژه در شناسه قطعه خالی است");
            }
            return new PieceRef(projectCode, rowNum, pieceNumber);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("شماره ردیف و شماره قطعه باید عدد باشند، مثلاً 498-2-54");
        }
    }

    private record PieceRef(String projectCode, int rowNum, int pieceNumber) {}

    public PieceResponse updatePiece(UUID id, UpdatePieceRequest request) {
        Piece piece = findPieceOrThrow(id);
        assertNotArchived(piece);

        boolean exists = pieceRepository.existsByPieceDefinitionIdAndPieceNumberAndIdNot(
                piece.getPieceDefinition().getId(),
                request.getPieceNumber(),
                id
        );

        if (exists) {
            throw new DuplicateResourceException(
                    "قطعه‌ای با شماره " + request.getPieceNumber() +
                            " برای این تعریف قطعه از قبل وجود دارد"
            );
        }

        piece.setPieceNumber(request.getPieceNumber());
        Piece saved = pieceRepository.save(piece);
        return toResponse(saved);
    }

    public void deletePiece(UUID id, String username) {
        Piece piece = findPieceOrThrow(id);
        assertNotArchived(piece);
        piece.setArchivedAt(LocalDateTime.now());
        piece.setArchivedBy(resolveDisplayName(username));
        pieceRepository.save(piece);
    }

    public PieceResponse restorePiece(UUID id) {
        Piece piece = findPieceOrThrow(id);
        if (piece.getArchivedAt() == null) {
            throw new IllegalStateException("این قطعه بایگانی نشده است");
        }
        piece.setArchivedAt(null);
        piece.setArchivedBy(null);
        return toResponse(pieceRepository.save(piece));
    }

    public BulkArchiveResultResponse bulkArchivePieces(List<UUID> pieceIds, String username) {
        List<Piece> pieces = loadPiecesOrThrow(pieceIds);
        List<Piece> toArchive = pieces.stream().filter(p -> p.getArchivedAt() == null).toList();
        if (toArchive.isEmpty()) {
            throw new IllegalStateException("همه قطعات انتخاب‌شده از قبل بایگانی شده‌اند");
        }
        archivePieces(toArchive, username);
        return new BulkArchiveResultResponse(toArchive.size());
    }

    public BulkArchiveResultResponse bulkRestorePieces(List<UUID> pieceIds) {
        List<Piece> pieces = loadPiecesOrThrow(pieceIds);
        List<Piece> toRestore = pieces.stream().filter(p -> p.getArchivedAt() != null).toList();
        if (toRestore.isEmpty()) {
            throw new IllegalStateException("هیچ‌کدام از قطعات انتخاب‌شده بایگانی نشده‌اند");
        }
        restorePieces(toRestore);
        return new BulkArchiveResultResponse(toRestore.size());
    }

    public BulkArchiveResultResponse archivePiecesByDefinition(UUID pieceDefinitionId, String username) {
        pieceDefinitionService.findDefinitionOrThrow(pieceDefinitionId);
        List<Piece> toArchive = pieceRepository.findByPieceDefinitionIdAndArchivedAtIsNull(pieceDefinitionId);
        if (toArchive.isEmpty()) {
            throw new IllegalStateException("قطعه‌ی فعالی در این ردیف برای بایگانی وجود ندارد");
        }
        archivePieces(toArchive, username);
        return new BulkArchiveResultResponse(toArchive.size());
    }

    public BulkArchiveResultResponse archivePiecesByProject(UUID projectId, String username) {
        projectService.findProjectOrThrow(projectId);
        List<Piece> toArchive = pieceRepository.findByArchivedAtIsNullAndPieceDefinition_Project_Id(projectId);
        if (toArchive.isEmpty()) {
            throw new IllegalStateException("قطعه‌ی فعالی در این پروژه برای بایگانی وجود ندارد");
        }
        archivePieces(toArchive, username);
        return new BulkArchiveResultResponse(toArchive.size());
    }

    @Transactional(readOnly = true)
    public RowPieceStatusReportResponse getRowStatusReport(UUID pieceDefinitionId) {
        PieceDefinition definition = pieceDefinitionService.findDefinitionOrThrow(pieceDefinitionId);
        List<Piece> pieces = pieceRepository.findAllWithDefinitionAndProjectByDefinitionId(pieceDefinitionId);
        Map<UUID, Long> defectsCounts = defectsCountMapFor(pieces);
        Map<UUID, LocalDateTime> stageEnteredAtMap = stageEnteredAtMapFor(pieces);

        List<PieceResponse> pieceResponses = pieces.stream()
                .map(p -> toResponse(
                        p,
                        defectsCounts.getOrDefault(p.getId(), 0L),
                        stageEnteredAtMap.getOrDefault(p.getId(), p.getCreatedAt())
                ))
                .toList();

        Map<PieceStage, int[]> counts = new EnumMap<>(PieceStage.class);
        for (PieceStage stage : PieceStage.values()) {
            counts.put(stage, new int[]{0, 0});
        }

        int activeCount = 0;
        int archivedCount = 0;
        for (Piece piece : pieces) {
            int[] bucket = counts.get(piece.getCurrentStage());
            if (piece.getArchivedAt() != null) {
                bucket[1]++;
                archivedCount++;
            } else {
                bucket[0]++;
                activeCount++;
            }
        }

        List<StageStatusCount> stageCounts = new ArrayList<>();
        for (PieceStage stage : PieceStage.values()) {
            int[] bucket = counts.get(stage);
            stageCounts.add(new StageStatusCount(stage, bucket[0], bucket[1], bucket[0] + bucket[1]));
        }

        return new RowPieceStatusReportResponse(
                definition.getId(),
                definition.getProject().getId(),
                definition.getProject().getCode(),
                definition.getProject().getName(),
                definition.getRowNum(),
                definition.getPieceName(),
                pieces.size(),
                activeCount,
                archivedCount,
                stageCounts,
                pieceResponses
        );
    }

    private List<Piece> loadPiecesOrThrow(List<UUID> pieceIds) {
        List<Piece> pieces = pieceRepository.findAllById(pieceIds);
        if (pieces.size() != pieceIds.size()) {
            throw new ResourceNotFoundException("برخی از قطعات انتخاب‌شده پیدا نشدند");
        }
        return pieces;
    }

    private void archivePieces(List<Piece> pieces, String username) {
        LocalDateTime now = LocalDateTime.now();
        String archivedBy = resolveDisplayName(username);
        for (Piece piece : pieces) {
            piece.setArchivedAt(now);
            piece.setArchivedBy(archivedBy);
        }
        pieceRepository.saveAll(pieces);
    }

    private void restorePieces(List<Piece> pieces) {
        for (Piece piece : pieces) {
            piece.setArchivedAt(null);
            piece.setArchivedBy(null);
        }
        pieceRepository.saveAll(pieces);
    }

    public PieceResponse updateProcessDate(UUID id, UpdateProcessDateRequest request) {
        Piece piece = findPieceOrThrow(id);
        assertNotArchived(piece);
        piece.setProcessDate(request.getProcessDate());
        upsertStageProcessDate(piece, piece.getCurrentStage(), request.getProcessDate());
        upsertCurrentSubStageProcessDate(piece, piece.getCurrentStage(), request.getProcessDate());
        return toResponse(pieceRepository.save(piece));
    }

    /**
     * ثبت یا پاک کردن تاریخ انجامِ یک زیرمرحله‌ی مشخص، بدون اثر بر زیرمرحله‌های دیگر.
     * اگر همان زیرمرحله، موقعیت فعلی قطعه باشد، تاریخ جاری قطعه (ستون ردیابی) هم هم‌گام می‌شود.
     */
    public void updateSubStageProcessDate(UUID id, UpdateSubStageProcessDateRequest request) {
        Piece piece = findPieceOrThrow(id);
        assertNotArchived(piece);
        SubStageKind kind = request.getKind();
        String subStage = request.getSubStage().trim();
        validateSubStage(kind, subStage);

        upsertSubStageProcessDate(piece, kind, subStage, request.getProcessDate());

        PieceStage currentStage = piece.getCurrentStage();
        if (kind == subStageKindOf(currentStage) && subStage.equals(subStageValueOf(piece, currentStage))) {
            piece.setProcessDate(request.getProcessDate());
            upsertStageProcessDate(piece, currentStage, request.getProcessDate());
            pieceRepository.save(piece);
        }
    }

    private static void validateSubStage(SubStageKind kind, String subStage) {
        boolean valid;
        try {
            switch (kind) {
                case RADIOGRAPHY -> valid = RadiographySubStage.valueOf(subStage) != null;
                case HEAT_TREATMENT -> valid = HeatTreatmentSubStage.valueOf(subStage) != null;
                case ROUGH_MACHINING -> valid = RoughMachiningSubStage.valueOf(subStage) != null;
                default -> valid = false;
            }
        } catch (IllegalArgumentException ex) {
            valid = false;
        }
        if (!valid) {
            throw new IllegalArgumentException("زیرمرحله‌ی نامعتبر است");
        }
    }

    @Transactional(readOnly = true)
    public List<SubStageProcessDateResponse> getSubStageProcessDates(UUID pieceId) {
        findPieceOrThrow(pieceId);
        return pieceSubStageProcessDateRepository.findByPiece_Id(pieceId).stream()
                .map(row -> new SubStageProcessDateResponse(row.getKind(), row.getSubStage(), row.getProcessDate()))
                .toList();
    }

    public void updateStageProcessDate(UUID id, UpdateStageProcessDateRequest request) {
        Piece piece = findPieceOrThrow(id);
        assertNotArchived(piece);
        if (request.getStage() == null) {
            throw new IllegalArgumentException("مرحله را مشخص کنید");
        }
        upsertStageProcessDate(piece, request.getStage(), request.getProcessDate());
        if (piece.getCurrentStage() == request.getStage()) {
            piece.setProcessDate(request.getProcessDate());
            pieceRepository.save(piece);
        }
    }
    @Transactional(readOnly = true)
    public Piece findPieceOrThrow(UUID id) {
        return pieceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "قطعه‌ای با شناسه " + id + " پیدا نشد"
                ));
    }
    private PieceResponse toResponse(Piece piece) {
        return toResponse(
                piece,
                radiographyDefectRepository.countOpenByPieceIdAndStatus(piece.getId(), DefectStatus.NEEDS_WELDING),
                resolveStageEnteredAt(piece)
        );
    }

    private List<PieceResponse> toResponses(
            List<Piece> pieces,
            Map<UUID, Long> defectsCounts,
            Map<UUID, LocalDateTime> stageEnteredAtMap
    ) {
        boolean admin = currentUserIsAdmin();
        return pieces.stream()
                .map(p -> toResponse(
                        p,
                        defectsCounts.getOrDefault(p.getId(), 0L),
                        stageEnteredAtMap.getOrDefault(p.getId(), p.getCreatedAt()),
                        admin
                ))
                .toList();
    }

    private PieceResponse toResponse(Piece piece, long defectsCount, LocalDateTime stageEnteredAt) {
        return toResponse(piece, defectsCount, stageEnteredAt, currentUserIsAdmin());
    }

    private PieceResponse toResponse(Piece piece, long defectsCount, LocalDateTime stageEnteredAt, boolean admin) {
        PieceDefinition definition = piece.getPieceDefinition();
        boolean hasDrawing = definition.getDrawingImagePath() != null
                && !definition.getDrawingImagePath().isBlank();
        AllowedPieceMoves moves = stageTransitionPolicy.allowedMoves(piece, admin);
        return new PieceResponse(
                piece.getId(),
                piece.getPieceDefinition().getId(),
                piece.getPieceDefinition().getPieceName(),
                piece.getPieceDefinition().getRowNum(),
                piece.getPieceNumber(),
                piece.getCurrentStage(),
                piece.getCreatedAt() ,
                piece.getPieceDefinition().getProject().getId() ,
                piece.getPieceDefinition().getProject().getCode(),
                piece.getDescription(),
                defectsCount,
                piece.getRadiographySubStage(),
                piece.getHeatTreatmentSubStage(),
                piece.getRoughMachiningSubStage(),
                stageEnteredAt,
                hasDrawing,
                definition.getMinUts(),
                definition.getMinYs(),
                definition.getMinEl(),
                definition.getMinHb(),
                piece.getProcessDate(),
                piece.getArchivedAt(),
                new ArrayList<>(moves.stages()),
                new ArrayList<>(moves.radiographySubStages()),
                new ArrayList<>(moves.heatTreatmentSubStages()),
                new ArrayList<>(moves.roughMachiningSubStages()),
                stageTransitionPolicy.forwardMove(piece),
                null
        );
    }

    private boolean currentUserIsAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return false;
        }
        return permissionService.isAdmin(auth.getName());
    }

    private void recordSubStageHistory(Piece piece, SubStageKind kind, String subStage, String changedByUsername) {
        if (subStage == null || subStage.isBlank()) {
            return;
        }
        PieceSubStageHistory history = new PieceSubStageHistory();
        history.setPiece(piece);
        history.setKind(kind);
        history.setSubStage(subStage);
        history.setChangedBy(resolveDisplayName(changedByUsername));
        pieceSubStageHistoryRepository.save(history);
    }

    /**
     * برای گرفتن تعداد عیوب چند قطعه به‌صورت یک‌جا (بدون N+1 Query) استفاده می‌شود.
     */
    private Map<UUID, Long> defectsCountMapFor(List<Piece> pieces) {
        if (pieces == null || pieces.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = pieces.stream().map(Piece::getId).toList();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : radiographyDefectRepository.countOpenDefectsGroupedByPieceIdInAndStatus(ids, DefectStatus.NEEDS_WELDING)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * تاریخ ورود قطعه به موقعیت فعلی (مرحله یا زیرمرحله).
     * اگر قطعه هنوز هیچ تغییر وضعیتی نداشته باشد، تاریخ ثبت خودِ قطعه برگردانده می‌شود.
     */
    private LocalDateTime resolveStageEnteredAt(Piece piece) {
        LocalDateTime stageAt = pieceStageHistoryRepository.findByPieceIdOrderByChangedAtDesc(piece.getId())
                .stream()
                .findFirst()
                .map(PieceStageHistory::getChangedAt)
                .orElse(piece.getCreatedAt());
        SubStageKind kind = currentSubStageKind(piece);
        if (kind == null) {
            return stageAt;
        }
        return pieceSubStageHistoryRepository
                .findFirstByPiece_IdAndKindAndSubStageOrderByChangedAtDesc(
                        piece.getId(),
                        kind,
                        currentSubStageValue(piece)
                )
                .map(PieceSubStageHistory::getChangedAt)
                .orElse(stageAt);
    }

    /**
     * نگاشتِ «تاریخ ورود به موقعیت فعلی» برای همه‌ی قطعات به‌صورت یک‌جا (بدون N+1 Query).
     */
    private Map<UUID, LocalDateTime> stageEnteredAtMapFor(List<Piece> pieces) {
        if (pieces == null || pieces.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = pieces.stream().map(Piece::getId).toList();
        Map<UUID, LocalDateTime> stageTimes = new HashMap<>();
        for (Object[] row : pieceStageHistoryRepository.findLatestChangedAtGroupedByPieceIdIn(ids)) {
            stageTimes.put((UUID) row[0], (LocalDateTime) row[1]);
        }
        Map<UUID, Map<String, LocalDateTime>> subTimesByPiece = new HashMap<>();
        for (Object[] row : pieceSubStageHistoryRepository.findLatestChangedAtGroupedByPieceKindAndSubStage(ids)) {
            UUID pieceId = (UUID) row[0];
            SubStageKind kind = (SubStageKind) row[1];
            String subStage = (String) row[2];
            LocalDateTime changedAt = (LocalDateTime) row[3];
            subTimesByPiece
                    .computeIfAbsent(pieceId, ignored -> new HashMap<>())
                    .put(kind.name() + "|" + subStage, changedAt);
        }
        Map<UUID, LocalDateTime> result = new HashMap<>();
        for (Piece piece : pieces) {
            LocalDateTime stageAt = stageTimes.getOrDefault(piece.getId(), piece.getCreatedAt());
            result.put(piece.getId(), overlaySubStageEnteredAt(piece, stageAt, subTimesByPiece.get(piece.getId())));
        }
        return result;
    }

    private static LocalDateTime overlaySubStageEnteredAt(
            Piece piece,
            LocalDateTime stageAt,
            Map<String, LocalDateTime> subTimes
    ) {
        SubStageKind kind = currentSubStageKind(piece);
        if (kind == null || subTimes == null) {
            return stageAt;
        }
        LocalDateTime subAt = subTimes.get(kind.name() + "|" + currentSubStageValue(piece));
        return subAt != null ? subAt : stageAt;
    }

    private static SubStageKind currentSubStageKind(Piece piece) {
        if (piece.getCurrentStage() == null) {
            return null;
        }
        return switch (piece.getCurrentStage()) {
            case RADIOGRAPHY -> SubStageKind.RADIOGRAPHY;
            case HEAT_TREATMENT -> SubStageKind.HEAT_TREATMENT;
            case ROUGH_MACHINING -> SubStageKind.ROUGH_MACHINING;
            default -> null;
        };
    }

    private static String currentSubStageValue(Piece piece) {
        return switch (piece.getCurrentStage()) {
            case RADIOGRAPHY -> StageTransitionPolicy.radioOf(piece).name();
            case HEAT_TREATMENT -> StageTransitionPolicy.heatOf(piece).name();
            case ROUGH_MACHINING -> StageTransitionPolicy.roughOf(piece).name();
            default -> null;
        };
    }
    public PieceResponse updateStage(UUID pieceId, UpdateStageRequest request, String changedByUsername) {
        return movePiece(
                pieceId,
                request.getNewStage(),
                changedByUsername,
                false,
                null,
                null,
                null,
                request.getScrap()
        );
    }

    /**
     * یک قدم خطی به جلو. اگر تاریخ انجام فرایند خالی باشد و موقعیت فعلی شروع‌نشده نباشد،
     * باید در همین درخواست بیاید. پس از انتقال، تاریخ موقعیت جدید خالی می‌ماند.
     */
    public PieceResponse advancePiece(UUID pieceId, AdvancePieceRequest request, String changedByUsername) {
        Piece piece = findPieceOrThrow(pieceId);
        assertNotArchived(piece);
        radiographyWorkflowGate.assertPieceNotInWorkflow(piece.getId());
        ForwardMoveResponse move = stageTransitionPolicy.forwardMove(piece);
        if (move == null || move.getKind() == null) {
            throw new IllegalStateException("برای این قطعه قدم بعدی در روال تولید وجود ندارد");
        }

        Permission required = Permission.valueOf(move.getRequiredPermission());
        if (!permissionService.hasPermission(changedByUsername, required)) {
            throw new AccessDeniedException("شما اجازه‌ی این انتقال را ندارید");
        }

        if (request != null && request.getProcessDate() != null) {
            piece.setProcessDate(request.getProcessDate());
        }
        if (move.isProcessDateRequired() && piece.getProcessDate() == null) {
            throw new IllegalStateException("تاریخ انجام فرایند را وارد کنید تا قطعه به مرحله بعد برود");
        }

        switch (move.getKind()) {
            case STAGE -> applyStageChange(
                    piece,
                    move.getStage(),
                    changedByUsername,
                    false,
                    move.getRadiographySubStage(),
                    move.getHeatTreatmentSubStage(),
                    move.getRoughMachiningSubStage()
            );
            case RADIOGRAPHY_SUBSTAGE -> {
                stageTransitionPolicy.assertRadiographySubStageAllowed(
                        piece, move.getRadiographySubStage(), false
                );
                snapshotOutgoingProcessDate(piece);
                piece.setRadiographySubStage(move.getRadiographySubStage());
                recordSubStageHistory(
                        piece,
                        SubStageKind.RADIOGRAPHY,
                        move.getRadiographySubStage().name(),
                        changedByUsername
                );
            }
            case HEAT_TREATMENT_SUBSTAGE -> {
                stageTransitionPolicy.assertHeatTreatmentSubStageAllowed(
                        piece, move.getHeatTreatmentSubStage(), false
                );
                snapshotOutgoingProcessDate(piece);
                piece.setHeatTreatmentSubStage(move.getHeatTreatmentSubStage());
                recordSubStageHistory(
                        piece,
                        SubStageKind.HEAT_TREATMENT,
                        move.getHeatTreatmentSubStage().name(),
                        changedByUsername
                );
            }
            case ROUGH_MACHINING_SUBSTAGE -> {
                stageTransitionPolicy.assertRoughMachiningSubStageAllowed(
                        piece, move.getRoughMachiningSubStage(), false
                );
                snapshotOutgoingProcessDate(piece);
                piece.setRoughMachiningSubStage(move.getRoughMachiningSubStage());
                recordSubStageHistory(
                        piece,
                        SubStageKind.ROUGH_MACHINING,
                        move.getRoughMachiningSubStage().name(),
                        changedByUsername
                );
            }
        }

        return toResponse(pieceRepository.save(piece));
    }

    /** Called inside the passport approval transaction, after all linked pieces were locked. */
    public void advanceByMeltApproval(UUID pieceId, LocalDate meltDate, String username) {
        if (!permissionService.hasPermission(username, Permission.APPROVE_MELT_PASSPORT)) {
            throw new AccessDeniedException("مجوز تأیید شناسنامه ذوب لازم است");
        }
        Piece piece = findPieceOrThrow(pieceId);
        assertNotArchived(piece);
        if (piece.getCurrentStage() != PieceStage.CASTING) {
            throw new IllegalStateException("تمام قطعات شناسنامه باید در مرحله ذوب‌ریزی باشند");
        }
        ForwardMoveResponse move = stageTransitionPolicy.forwardMove(piece);
        if (move == null || move.getKind() != ForwardMoveKind.STAGE) {
            throw new IllegalStateException("مرحله بعد ذوب‌ریزی در گردش تولید تعریف نشده است");
        }
        if (piece.getProcessDate() == null) piece.setProcessDate(meltDate);
        applyStageChange(piece, move.getStage(), username, false, move.getRadiographySubStage(),
                move.getHeatTreatmentSubStage(), move.getRoughMachiningSubStage());
        pieceRepository.save(piece);
    }

    /**
     * انتقال قطعه از «تفسیر رادیوگرافی» به «VT» پس از تأیید نهایی مدیر. فقط RadiographyWorkflowService صدایش
     * می‌زند (قاعده‌ی کسب‌وکار در بک‌اند است و UI مرحله را مستقیم عوض نمی‌کند). شرط مرحله‌ی فعلی را خودش
     * بررسی می‌کند و چون وضعیت قطعه را از روی دیتابیس می‌خواند، تأیید روی قطعه‌ای که جابه‌جا شده خطا می‌دهد.
     * تاریخ فرایند مرحله‌ی خروجی مثل انتقال عادی در تاریخچه ثبت می‌شود (بدون الزام به وارد کردن آن).
     */
    public void moveInterpretationToVtByWorkflow(UUID pieceId, String changedByUsername) {
        Piece piece = findPieceOrThrow(pieceId);
        assertNotArchived(piece);
        if (piece.getCurrentStage() != PieceStage.RADIOGRAPHY
                || piece.getRadiographySubStage() != RadiographySubStage.INTERPRETATION) {
            throw new IllegalStateException("قطعه دیگر در مرحله‌ی تفسیر رادیوگرافی نیست؛ انتقال به VT انجام نشد");
        }
        snapshotOutgoingProcessDate(piece);
        piece.setRadiographySubStage(RadiographySubStage.VT);
        pieceRepository.save(piece);
        recordSubStageHistory(piece, SubStageKind.RADIOGRAPHY, RadiographySubStage.VT.name(), changedByUsername);
    }

    /**
     * انتقال مورد اعتماد برای تعیین‌تکلیف کشش و اتمام جوشکاری؛ از گراف کاربر عبور می‌کند
     * ولی ورود به جوشکاری از مسیر مرحله همچنان ممنوع است.
     */
    public PieceResponse applyTrustedStageMove(
            UUID pieceId,
            PieceStage newStage,
            RadiographySubStage radiographyOverride,
            HeatTreatmentSubStage heatTreatmentOverride,
            RoughMachiningSubStage roughMachiningOverride,
            String changedByUsername
    ) {
        return movePiece(
                pieceId,
                newStage,
                changedByUsername,
                true,
                radiographyOverride,
                heatTreatmentOverride,
                roughMachiningOverride
        );
    }

    public PieceResponse applyTrustedStageMove(UUID pieceId, PieceStage stage, String username, ScrapRequest scrap) {
        return movePiece(pieceId, stage, username, true, null, null, null, scrap);
    }

    private PieceResponse movePiece(
            UUID pieceId,
            PieceStage newStage,
            String changedByUsername,
            boolean trusted,
            RadiographySubStage radiographyOverride,
            HeatTreatmentSubStage heatTreatmentOverride,
            RoughMachiningSubStage roughMachiningOverride
    ) {
        return movePiece(pieceId, newStage, changedByUsername, trusted, radiographyOverride,
                heatTreatmentOverride, roughMachiningOverride, null);
    }

    private PieceResponse movePiece(UUID pieceId, PieceStage newStage, String changedByUsername, boolean trusted,
            RadiographySubStage radiographyOverride, HeatTreatmentSubStage heatTreatmentOverride,
            RoughMachiningSubStage roughMachiningOverride, ScrapRequest scrap) {
        Piece piece = pieceRepository.lockById(pieceId).orElseThrow(() -> new ResourceNotFoundException("قطعه پیدا نشد"));
        assertNotArchived(piece);
        boolean admin = permissionService.isAdmin(changedByUsername);
        applyStageChange(
                piece,
                newStage,
                changedByUsername,
                trusted || admin,
                radiographyOverride,
                heatTreatmentOverride,
                roughMachiningOverride,
                scrap
        );
        Piece saved = pieceRepository.save(piece);
        return toResponse(saved);
    }

    /**
     * پیشرفت بین زیرمرحله‌های داخلی رادیوگرافی؛ وضعیت فعلی و تاریخچه ثبت می‌شود.
     */
    public PieceResponse updateRadiographySubStage(UUID pieceId, UpdateRadiographySubStageRequest request, String changedByUsername) {
        Piece piece = findPieceOrThrow(pieceId);
        assertNotArchived(piece);
        radiographyWorkflowGate.assertPieceNotInWorkflow(piece.getId());
        stageTransitionPolicy.assertRadiographySubStageAllowed(
                piece, request.getRadiographySubStage(), permissionService.isAdmin(changedByUsername)
        );

        piece.setRadiographySubStage(request.getRadiographySubStage());
        Piece saved = pieceRepository.save(piece);
        recordSubStageHistory(saved, SubStageKind.RADIOGRAPHY, request.getRadiographySubStage().name(), changedByUsername);
        return toResponse(saved);
    }

    /**
     * پیشرفت بین زیرمرحله‌های داخلی عملیات حرارتی؛ وضعیت فعلی و تاریخچه ثبت می‌شود.
     */
    public PieceResponse updateHeatTreatmentSubStage(UUID pieceId, UpdateHeatTreatmentSubStageRequest request, String changedByUsername) {
        Piece piece = findPieceOrThrow(pieceId);
        assertNotArchived(piece);
        stageTransitionPolicy.assertHeatTreatmentSubStageAllowed(
                piece, request.getHeatTreatmentSubStage(), permissionService.isAdmin(changedByUsername)
        );

        piece.setHeatTreatmentSubStage(request.getHeatTreatmentSubStage());
        Piece saved = pieceRepository.save(piece);
        recordSubStageHistory(saved, SubStageKind.HEAT_TREATMENT, request.getHeatTreatmentSubStage().name(), changedByUsername);
        return toResponse(saved);
    }

    /**
     * پیشرفت بین زیرمرحله‌های خشن‌تراشی. کاربران عادی فقط رو به جلو؛ ادمین برای اصلاح آزاد است.
     */
    public PieceResponse updateRoughMachiningSubStage(UUID pieceId, UpdateRoughMachiningSubStageRequest request, String changedByUsername) {
        Piece piece = findPieceOrThrow(pieceId);
        assertNotArchived(piece);
        RoughMachiningSubStage target = request.getRoughMachiningSubStage();
        stageTransitionPolicy.assertRoughMachiningSubStageAllowed(
                piece, target, permissionService.isAdmin(changedByUsername)
        );

        piece.setRoughMachiningSubStage(target);
        Piece saved = pieceRepository.save(piece);
        recordSubStageHistory(saved, SubStageKind.ROUGH_MACHINING, target.name(), changedByUsername);
        return toResponse(saved);
    }

    /**
     * تغییر وضعیت دسته‌جمعی چند قطعه هم‌زمان. عملیات همه‌یا-هیچ است: اگر حتی یکی از شناسه‌ها
     * پیدا نشود، کل عملیات رد می‌شود تا از تغییر نیمه‌کاره جلوگیری شود.
     */
    public List<PieceResponse> bulkUpdateStage(BulkUpdateStageRequest request, String changedByUsername) {
        List<Piece> pieces = request.getPieceIds().stream().distinct().sorted()
                .map(id -> pieceRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("قطعه پیدا نشد"))).toList();

        if (pieces.size() != request.getPieceIds().size()) {
            throw new ResourceNotFoundException("برخی از قطعات انتخاب‌شده پیدا نشدند");
        }
        pieces.forEach(this::assertNotArchived);

        PieceStage newStage = request.getNewStage();
        boolean trusted = permissionService.isAdmin(changedByUsername);
        if (newStage == PieceStage.WELDING) {
            throw new IllegalStateException(
                    "جوشکاری ایستگاه است نه مرحله؛ از بخش ارسال به جوشکاری در پروفایل قطعه استفاده کنید"
            );
        }
        List<Piece> toMove = pieces.stream()
                .filter(p -> p.getCurrentStage() != newStage)
                .filter(p -> p.getCurrentStage() != PieceStage.WELDING || newStage == PieceStage.SCRAPPED || trusted)
                .toList();
        if (toMove.isEmpty()) {
            boolean allWelding = pieces.stream().allMatch(p -> p.getCurrentStage() == PieceStage.WELDING);
            if (allWelding && newStage != PieceStage.SCRAPPED && !trusted) {
                throw new IllegalStateException("برای خروج از جوشکاری دکمهٔ «اتمام فرآیند» را در پروفایل قطعه بزنید");
            }
            throw new IllegalStateException("همه قطعات انتخاب‌شده هم‌اکنون در این مرحله هستند");
        }

        for (Piece piece : toMove) {
            applyStageChange(piece, newStage, changedByUsername, trusted, null, null, null, request.getScrap());
        }

        List<Piece> saved = pieceRepository.saveAll(toMove);
        Map<UUID, Long> defectsCounts = defectsCountMapFor(saved);
        Map<UUID, LocalDateTime> stageEnteredAtMap = stageEnteredAtMapFor(saved);

        return toResponses(saved, defectsCounts, stageEnteredAtMap);
    }

    /**
     * تغییر دسته‌جمعی زیرمرحله‌ی رادیوگرافی برای چند قطعه هم‌زمان. عملیات همه‌یا-هیچ است:
     * اگر حتی یکی از قطعات در مرحله‌ی رادیوگرافی نباشد، کل عملیات رد می‌شود.
     */
    public List<PieceResponse> bulkUpdateRadiographySubStage(BulkUpdateRadiographySubStageRequest request, String changedByUsername) {
        List<Piece> pieces = pieceRepository.findAllById(request.getPieceIds());

        if (pieces.size() != request.getPieceIds().size()) {
            throw new ResourceNotFoundException("برخی از قطعات انتخاب‌شده پیدا نشدند");
        }
        pieces.forEach(this::assertNotArchived);

        boolean anyNotInRadiography = pieces.stream()
                .anyMatch(p -> p.getCurrentStage() != PieceStage.RADIOGRAPHY);

        if (anyNotInRadiography) {
            throw new IllegalStateException(
                    "زیرمرحله‌ی رادیوگرافی فقط برای قطعاتی که در مرحله‌ی رادیوگرافی هستند قابل تغییر است"
            );
        }

        boolean admin = permissionService.isAdmin(changedByUsername);
        List<Piece> toMove = pieces.stream()
                .filter(p -> p.getRadiographySubStage() != request.getRadiographySubStage())
                .toList();
        if (toMove.isEmpty()) {
            throw new IllegalStateException("همه قطعات انتخاب‌شده هم‌اکنون در این زیرمرحله هستند");
        }
        toMove.forEach(p -> radiographyWorkflowGate.assertPieceNotInWorkflow(p.getId()));
        toMove.forEach(p -> stageTransitionPolicy.assertRadiographySubStageAllowed(
                p, request.getRadiographySubStage(), admin
        ));

        toMove.forEach(p -> p.setRadiographySubStage(request.getRadiographySubStage()));
        List<Piece> saved = pieceRepository.saveAll(toMove);
        String displayName = resolveDisplayName(changedByUsername);
        List<PieceSubStageHistory> subHistories = saved.stream().map(p -> {
            PieceSubStageHistory sh = new PieceSubStageHistory();
            sh.setPiece(p);
            sh.setKind(SubStageKind.RADIOGRAPHY);
            sh.setSubStage(request.getRadiographySubStage().name());
            sh.setChangedBy(displayName);
            return sh;
        }).toList();
        pieceSubStageHistoryRepository.saveAll(subHistories);

        Map<UUID, Long> defectsCounts = defectsCountMapFor(saved);
        Map<UUID, LocalDateTime> stageEnteredAtMap = stageEnteredAtMapFor(saved);

        return saved.stream()
                .map(p -> toResponse(
                        p,
                        defectsCounts.getOrDefault(p.getId(), 0L),
                        stageEnteredAtMap.getOrDefault(p.getId(), p.getCreatedAt())
                ))
                .toList();
    }

    /**
     * تغییر دسته‌جمعی زیرمرحله‌ی عملیات حرارتی برای چند قطعه هم‌زمان. عملیات همه‌یا-هیچ است:
     * اگر حتی یکی از قطعات در مرحله‌ی عملیات حرارتی نباشد، کل عملیات رد می‌شود.
     */
    public List<PieceResponse> bulkUpdateHeatTreatmentSubStage(BulkUpdateHeatTreatmentSubStageRequest request, String changedByUsername) {
        List<Piece> pieces = pieceRepository.findAllById(request.getPieceIds());

        if (pieces.size() != request.getPieceIds().size()) {
            throw new ResourceNotFoundException("برخی از قطعات انتخاب‌شده پیدا نشدند");
        }
        pieces.forEach(this::assertNotArchived);

        boolean anyNotInHeatTreatment = pieces.stream()
                .anyMatch(p -> p.getCurrentStage() != PieceStage.HEAT_TREATMENT);

        if (anyNotInHeatTreatment) {
            throw new IllegalStateException(
                    "زیرمرحله‌ی عملیات حرارتی فقط برای قطعاتی که در مرحله‌ی عملیات حرارتی هستند قابل تغییر است"
            );
        }

        boolean admin = permissionService.isAdmin(changedByUsername);
        List<Piece> toMove = pieces.stream()
                .filter(p -> p.getHeatTreatmentSubStage() != request.getHeatTreatmentSubStage())
                .toList();
        if (toMove.isEmpty()) {
            throw new IllegalStateException("همه قطعات انتخاب‌شده هم‌اکنون در این زیرمرحله هستند");
        }
        toMove.forEach(p -> stageTransitionPolicy.assertHeatTreatmentSubStageAllowed(
                p, request.getHeatTreatmentSubStage(), admin
        ));

        toMove.forEach(p -> p.setHeatTreatmentSubStage(request.getHeatTreatmentSubStage()));
        List<Piece> saved = pieceRepository.saveAll(toMove);
        String displayName = resolveDisplayName(changedByUsername);
        List<PieceSubStageHistory> subHistories = saved.stream().map(p -> {
            PieceSubStageHistory sh = new PieceSubStageHistory();
            sh.setPiece(p);
            sh.setKind(SubStageKind.HEAT_TREATMENT);
            sh.setSubStage(request.getHeatTreatmentSubStage().name());
            sh.setChangedBy(displayName);
            return sh;
        }).toList();
        pieceSubStageHistoryRepository.saveAll(subHistories);

        Map<UUID, Long> defectsCounts = defectsCountMapFor(saved);
        Map<UUID, LocalDateTime> stageEnteredAtMap = stageEnteredAtMapFor(saved);

        return saved.stream()
                .map(p -> toResponse(
                        p,
                        defectsCounts.getOrDefault(p.getId(), 0L),
                        stageEnteredAtMap.getOrDefault(p.getId(), p.getCreatedAt())
                ))
                .toList();
    }

    /**
     * تغییر دسته‌جمعی زیرمرحله‌ی خشن‌تراشی. مثل بقیه‌ی عملیات دسته‌جمعی همه‌یا-هیچ است:
     * اگر حتی یک قطعه در مرحله‌ی خشن‌تراشی نباشد یا عقب‌گرد غیرمجاز داشته باشد،
     * کل درخواست رد می‌شود تا دسته نیمه‌کاره تغییر نکند.
     */
    public List<PieceResponse> bulkUpdateRoughMachiningSubStage(BulkUpdateRoughMachiningSubStageRequest request, String changedByUsername) {
        List<Piece> pieces = pieceRepository.findAllById(request.getPieceIds());

        if (pieces.size() != request.getPieceIds().size()) {
            throw new ResourceNotFoundException("برخی از قطعات انتخاب‌شده پیدا نشدند");
        }
        pieces.forEach(this::assertNotArchived);

        boolean anyNotInRoughMachining = pieces.stream()
                .anyMatch(p -> p.getCurrentStage() != PieceStage.ROUGH_MACHINING);

        if (anyNotInRoughMachining) {
            throw new IllegalStateException(
                    "زیرمرحله‌ی خشن‌تراشی فقط برای قطعاتی که در مرحله‌ی خشن‌تراشی هستند قابل تغییر است"
            );
        }

        boolean admin = permissionService.isAdmin(changedByUsername);
        RoughMachiningSubStage target = request.getRoughMachiningSubStage();
        List<Piece> toMove = pieces.stream()
                .filter(p -> p.getRoughMachiningSubStage() != target)
                .toList();
        if (toMove.isEmpty()) {
            throw new IllegalStateException("همه قطعات انتخاب‌شده هم‌اکنون در این زیرمرحله هستند");
        }

        toMove.forEach(p -> stageTransitionPolicy.assertRoughMachiningSubStageAllowed(p, target, admin));

        toMove.forEach(p -> p.setRoughMachiningSubStage(target));
        List<Piece> saved = pieceRepository.saveAll(toMove);
        String displayName = resolveDisplayName(changedByUsername);
        List<PieceSubStageHistory> subHistories = saved.stream().map(p -> {
            PieceSubStageHistory sh = new PieceSubStageHistory();
            sh.setPiece(p);
            sh.setKind(SubStageKind.ROUGH_MACHINING);
            sh.setSubStage(target.name());
            sh.setChangedBy(displayName);
            return sh;
        }).toList();
        pieceSubStageHistoryRepository.saveAll(subHistories);

        Map<UUID, Long> defectsCounts = defectsCountMapFor(saved);
        Map<UUID, LocalDateTime> stageEnteredAtMap = stageEnteredAtMapFor(saved);

        return saved.stream()
                .map(p -> toResponse(
                        p,
                        defectsCounts.getOrDefault(p.getId(), 0L),
                        stageEnteredAtMap.getOrDefault(p.getId(), p.getCreatedAt())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PieceStageHistoryResponse> getStageHistory(UUID pieceId) {
        Piece piece = findPieceOrThrow(pieceId);
        Map<PieceStage, LocalDate> dates = new EnumMap<>(PieceStage.class);
        pieceStageProcessDateRepository.findByPiece_IdIn(List.of(pieceId))
                .forEach(row -> dates.put(row.getStage(), row.getProcessDate()));
        if (piece.getProcessDate() != null) dates.put(piece.getCurrentStage(), piece.getProcessDate());
        return pieceStageHistoryRepository.findByPieceIdOrderByChangedAtDesc(pieceId)
                .stream()
                .map(h -> new PieceStageHistoryResponse(h.getId(), h.getStage(), h.getChangedAt(), h.getChangedBy(), dates.get(h.getStage())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StageProcessDateResponse> getStageProcessDates(UUID pieceId) {
        findPieceOrThrow(pieceId);
        return pieceStageProcessDateRepository.findByPiece_IdIn(List.of(pieceId)).stream()
                .map(row -> new StageProcessDateResponse(row.getStage(), row.getProcessDate()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PieceSubStageHistoryResponse> getSubStageHistory(UUID pieceId) {
        Piece piece = findPieceOrThrow(pieceId);
        Map<String, LocalDate> dates = new HashMap<>();
        pieceSubStageProcessDateRepository.findByPiece_Id(pieceId)
                .forEach(row -> dates.put(subStageDateKey(row.getKind(), row.getSubStage()), row.getProcessDate()));
        if (piece.getProcessDate() != null && currentSubStageKind(piece) != null) {
            dates.put(subStageDateKey(currentSubStageKind(piece), currentSubStageValue(piece)), piece.getProcessDate());
        }
        return pieceSubStageHistoryRepository.findByPieceIdOrderByChangedAtDesc(pieceId)
                .stream()
                .map(h -> new PieceSubStageHistoryResponse(
                        h.getId(),
                        h.getKind(),
                        h.getSubStage(),
                        h.getChangedAt(),
                        h.getChangedBy(),
                        dates.get(subStageDateKey(h.getKind(), h.getSubStage()))
                ))
                .toList();
    }

    private static String subStageDateKey(SubStageKind kind, String value) {
        String canonical = kind == SubStageKind.RADIOGRAPHY && value != null && !value.isBlank()
                ? RadiographySubStage.fromStored(value).name() : value;
        return kind + "|" + canonical;
    }

    @Transactional(readOnly = true)
    public List<PieceResponse> getAllPieces() {
        List<Piece> pieces = pieceRepository.findByArchivedAtIsNull();
        Map<UUID, Long> defectsCounts = defectsCountMapFor(pieces);
        Map<UUID, LocalDateTime> stageEnteredAtMap = stageEnteredAtMapFor(pieces);

        return pieces.stream()
                .map(p -> toResponse(
                        p,
                        defectsCounts.getOrDefault(p.getId(), 0L),
                        stageEnteredAtMap.getOrDefault(p.getId(), p.getCreatedAt())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PieceResponse> searchPieces(String keyword) {
        PieceSearchTerm term = PieceSearchTerm.parse(keyword);
        List<Piece> pieces;
        if (term.keyword().isEmpty()) {
            pieces = pieceRepository.findByArchivedAtIsNull();
        } else if (term.numeric()) {
            pieces = term.pieceNumber() == null ? List.of()
                    : pieceRepository.findByPieceNumberAndArchivedAtIsNull(term.pieceNumber());
        } else {
            pieces = pieceRepository.searchPieces(term.keyword());
        }

        Map<UUID, Long> defectsCounts = defectsCountMapFor(pieces);
        Map<UUID, LocalDateTime> stageEnteredAtMap = stageEnteredAtMapFor(pieces);

        return pieces.stream()
                .map(p -> toResponse(
                        p,
                        defectsCounts.getOrDefault(p.getId(), 0L),
                        stageEnteredAtMap.getOrDefault(p.getId(), p.getCreatedAt())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PieceResponse> listTrackingPieces(
            String keyword,
            String pieceName,
            String projectCode,
            PieceStage stage,
            LocalDate dateFrom,
            LocalDate dateTo,
            RadiographySubStage radiographySubStage,
            HeatTreatmentSubStage heatTreatmentSubStage,
            RoughMachiningSubStage roughMachiningSubStage,
            String sortBy,
            String sortDir,
            int page,
            int size
    ) {
        int safeSize = Math.min(100, Math.max(1, size));
        int safePage = Math.max(0, page);
        TrackingListFilter filter = trackingFilter(
                keyword,
                pieceName,
                projectCode,
                stage,
                dateFrom,
                dateTo,
                radiographySubStage,
                heatTreatmentSubStage,
                roughMachiningSubStage,
                sortBy,
                sortDir,
                true
        );
        var idPage = pieceRepository.findTrackingPieceIds(
                filter,
                org.springframework.data.domain.PageRequest.of(safePage, safeSize)
        );
        List<UUID> ids = idPage.getContent();
        List<Piece> ordered = List.of();
        if (!ids.isEmpty()) {
            Map<UUID, Piece> byId = pieceRepository.findWithDefinitionAndProjectByIdIn(ids).stream()
                    .collect(Collectors.toMap(Piece::getId, p -> p));
            ordered = ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        }
        Map<UUID, Long> defectsCounts = defectsCountMapFor(ordered);
        Map<UUID, LocalDateTime> stageEnteredAtMap = stageEnteredAtMapFor(ordered);
        List<PieceResponse> items = toResponses(ordered, defectsCounts, stageEnteredAtMap);
        Map<UUID, TensileTrackingInfoResponse> tensileInfo = tensileTrackingService.forPieces(ordered);
        items.forEach(item -> item.setTensileTrackingInfo(tensileInfo.get(item.getId())));
        int totalPages = idPage.getTotalElements() == 0
                ? 0
                : (int) Math.ceil(idPage.getTotalElements() / (double) safeSize);
        return new PageResponse<>(items, idPage.getTotalElements(), safePage, safeSize, totalPages);
    }

    @Transactional(readOnly = true)
    public TrackingSummaryResponse getTrackingSummary(
            String keyword,
            String pieceName,
            String projectCode,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        TrackingListFilter filter = trackingFilter(
                keyword, pieceName, projectCode, null, dateFrom, dateTo, null, null, null, null, null, false
        );
        Map<PieceStage, Long> stageCounts = new EnumMap<>(PieceStage.class);
        for (PieceStage stage : PieceStage.values()) {
            stageCounts.put(stage, 0L);
        }
        long total = 0;
        for (Object[] row : pieceRepository.countTrackingGroupedByStage(filter)) {
            PieceStage stage = (PieceStage) row[0];
            long count = (Long) row[1];
            stageCounts.put(stage, count);
            total += count;
        }
        long notStarted = stageCounts.getOrDefault(PieceStage.NOT_STARTED, 0L);
        long delivered = stageCounts.getOrDefault(PieceStage.DELIVERED, 0L);
        long scrapped = stageCounts.getOrDefault(PieceStage.SCRAPPED, 0L);
        long ready = stageCounts.getOrDefault(PieceStage.READY, 0L);
        return new TrackingSummaryResponse(
                total,
                total - notStarted - delivered - scrapped - ready,
                notStarted,
                delivered,
                stageCounts
        );
    }

    private TrackingListFilter trackingFilter(
            String keyword,
            String pieceName,
            String projectCode,
            PieceStage stage,
            LocalDate dateFrom,
            LocalDate dateTo,
            RadiographySubStage radiographySubStage,
            HeatTreatmentSubStage heatTreatmentSubStage,
            RoughMachiningSubStage roughMachiningSubStage,
            String sortBy,
            String sortDir,
            boolean includeStageAndSubstage
    ) {
        return TrackingListFilter.builder()
                .keyword(keyword == null || keyword.isBlank() ? null : keyword.trim())
                .pieceName(blankToNull(pieceName))
                .projectCode(blankToNull(projectCode))
                .stage(stage)
                .enteredFrom(dateFrom == null ? null : dateFrom.atStartOfDay())
                .enteredToExclusive(dateTo == null ? null : dateTo.plusDays(1).atStartOfDay())
                .radiographySubStage(radiographySubStage)
                .heatTreatmentSubStage(heatTreatmentSubStage)
                .roughMachiningSubStage(roughMachiningSubStage)
                .sortBy(sortBy)
                .sortDir(sortDir)
                .includeStageAndSubstage(includeStageAndSubstage)
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public PieceResponse updateDescription(UUID pieceId, UpdateDescriptionRequest request, String username) {
        if (!permissionService.hasPermission(username, Permission.MANAGE_PIECES)) {
            throw new AccessDeniedException("برای تغییر توضیحات، دسترسی مدیریت قطعات لازم است");
        }
        Piece piece = findPieceOrThrow(pieceId);
        assertNotArchived(piece);
        piece.setDescription(request.getDescription());
        Piece saved = pieceRepository.save(piece);
        return toResponse(saved);
    }

    public void deleteStageHistory(UUID pieceId, UUID historyId, String requestingUsername) {
        requireAdmin(requestingUsername);
        findPieceOrThrow(pieceId);
        PieceStageHistory history = pieceStageHistoryRepository.findByIdAndPiece_Id(historyId, pieceId)
                .orElseThrow(() -> new ResourceNotFoundException("رکورد تاریخچه وضعیت پیدا نشد"));
        pieceStageHistoryRepository.delete(history);
    }

    public void deleteSubStageHistory(UUID pieceId, UUID historyId, String requestingUsername) {
        requireAdmin(requestingUsername);
        findPieceOrThrow(pieceId);
        PieceSubStageHistory history = pieceSubStageHistoryRepository.findByIdAndPiece_Id(historyId, pieceId)
                .orElseThrow(() -> new ResourceNotFoundException("رکورد تاریخچه زیرمرحله پیدا نشد"));
        pieceSubStageHistoryRepository.delete(history);
    }

    private void requireAdmin(String username) {
        if (!permissionService.isAdmin(username)) {
            throw new AccessDeniedException("فقط مدیر می‌تواند تاریخچه را حذف کند");
        }
    }

    private void assertNotArchived(Piece piece) {
        if (piece.getArchivedAt() != null) {
            throw new IllegalStateException("قطعه بایگانی‌شده قابل ویرایش نیست؛ ابتدا آن را بازگردانی کنید");
        }
    }

    private void applyStageChange(
            Piece piece,
            PieceStage newStage,
            String changedByUsername,
            boolean trusted,
            RadiographySubStage radiographyOverride,
            HeatTreatmentSubStage heatTreatmentOverride,
            RoughMachiningSubStage roughMachiningOverride
    ) {
        applyStageChange(piece,newStage,changedByUsername,trusted,radiographyOverride,heatTreatmentOverride,roughMachiningOverride,null);
    }

    private void applyStageChange(Piece piece, PieceStage newStage, String changedByUsername, boolean trusted,
            RadiographySubStage radiographyOverride, HeatTreatmentSubStage heatTreatmentOverride,
            RoughMachiningSubStage roughMachiningOverride, ScrapRequest scrap) {
        radiographyWorkflowGate.assertPieceNotInWorkflow(piece.getId());
        meltPassportGate.assertDepartureAllowed(piece, newStage);
        PieceStage oldStage = piece.getCurrentStage();
        if (newStage == PieceStage.WELDING) {
            throw new IllegalStateException(
                    "جوشکاری ایستگاه است نه مرحله؛ از بخش ارسال به جوشکاری در پروفایل قطعه استفاده کنید"
            );
        }

        boolean sameStage = oldStage == newStage;
        boolean heatSubOnly = sameStage
                && newStage == PieceStage.HEAT_TREATMENT
                && heatTreatmentOverride != null
                && heatTreatmentOverride != StageTransitionPolicy.heatOf(piece);

        if (sameStage && !heatSubOnly) {
            throw new IllegalStateException("این قطعه هم‌اکنون در همین مرحله است و انتقال تکراری مجاز نیست");
        }

        if (!trusted) {
            if (oldStage == PieceStage.WELDING && newStage != PieceStage.SCRAPPED) {
                throw new IllegalStateException("برای خروج از جوشکاری دکمهٔ «اتمام فرآیند» را در پروفایل قطعه بزنید");
            }
            stageTransitionPolicy.assertStageAllowed(piece, newStage, false);
        }

        if (newStage == PieceStage.SCRAPPED) scrapService.record(piece, scrap, changedByUsername);
        if (oldStage == PieceStage.SCRAPPED) scrapService.reverse(piece.getId(), changedByUsername);
        String displayName = resolveDisplayName(changedByUsername);

        if (oldStage == PieceStage.WELDING) {
            closeOpenWeldingDispatch(piece.getId(), displayName, false);
        }

        if (heatSubOnly) {
            snapshotOutgoingProcessDate(piece);
            piece.setHeatTreatmentSubStage(heatTreatmentOverride);
            recordSubStageHistory(piece, SubStageKind.HEAT_TREATMENT, heatTreatmentOverride.name(), changedByUsername);
            return;
        }

        if (newStage == PieceStage.CASTING && oldStage != PieceStage.CASTING) {
            meltPassportGate.beginCastingCycle(piece);
        }
        piece.setCurrentStage(newStage);
        applyDefaultProcessDateOnMove(piece, oldStage);

        if (newStage == PieceStage.RADIOGRAPHY && oldStage != PieceStage.RADIOGRAPHY) {
            RadiographySubStage radio = radiographyOverride != null ? radiographyOverride : RadiographySubStage.SHOT;
            piece.setRadiographySubStage(radio);
            recordSubStageHistory(piece, SubStageKind.RADIOGRAPHY, radio.name(), changedByUsername);
        }
        if (newStage == PieceStage.HEAT_TREATMENT && oldStage != PieceStage.HEAT_TREATMENT) {
            HeatTreatmentSubStage heat = heatTreatmentOverride != null
                    ? heatTreatmentOverride
                    : HeatTreatmentSubStage.AWAITING_OPERATIONS;
            piece.setHeatTreatmentSubStage(heat);
            recordSubStageHistory(piece, SubStageKind.HEAT_TREATMENT, heat.name(), changedByUsername);
        }
        if (newStage == PieceStage.ROUGH_MACHINING && oldStage != PieceStage.ROUGH_MACHINING) {
            RoughMachiningSubStage rough = roughMachiningOverride != null
                    ? roughMachiningOverride
                    : RoughMachiningSubStage.DEBURRING;
            piece.setRoughMachiningSubStage(rough);
            recordSubStageHistory(piece, SubStageKind.ROUGH_MACHINING, rough.name(), changedByUsername);
        }

        PieceStageHistory history = new PieceStageHistory();
        history.setPiece(piece);
        history.setStage(newStage);
        history.setChangedBy(displayName);
        pieceStageHistoryRepository.save(history);
    }

    private void closeOpenWeldingDispatch(UUID pieceId, String displayName, boolean confirmDefects) {
        pieceWeldingDispatchRepository.findFirstByPieceIdAndCompletedAtIsNullOrderBySentAtDesc(pieceId)
                .ifPresent(dispatch -> {
                    dispatch.setCompletedAt(LocalDateTime.now());
                    dispatch.setCompletedBy(displayName);
                    if (confirmDefects) {
                        confirmLinkedWeldingDefects(dispatch);
                    }
                    pieceWeldingDispatchRepository.save(dispatch);
                });
    }

    public PieceResponse sendToWeldingStation(UUID pieceId, SendToWeldingRequest request, String username) {
        Piece piece = findPieceOrThrow(pieceId);
        assertNotArchived(piece);
        meltPassportGate.assertDepartureAllowed(piece, PieceStage.WELDING);
        radiographyWorkflowGate.assertPieceNotInWorkflow(piece.getId());
        PieceStage oldStage = piece.getCurrentStage();
        if (oldStage == PieceStage.WELDING) {
            throw new IllegalStateException("این قطعه هم‌اکنون در ایستگاه جوشکاری است");
        }
        if (oldStage == PieceStage.DELIVERED || oldStage == PieceStage.SCRAPPED) {
            throw new IllegalStateException("قطعه تحویل‌شده یا اسقاط را نمی‌توان به جوشکاری ارسال کرد");
        }

        List<RadiographyDefect> selectedDefects = resolveOpenWeldingDefects(pieceId, request.getDefectIds());
        String storedType = DefectTypes.requireStoredType(request.getDefectType(), request.getDefectTypeCustom());
        String storedCustom = DefectTypes.storedCustom(storedType, request.getDefectTypeCustom());
        String notes = request.getNotes() == null ? "" : request.getNotes().trim();

        if (pieceWeldingDispatchRepository.findFirstByPieceIdAndCompletedAtIsNullOrderBySentAtDesc(pieceId).isPresent()) {
            throw new IllegalStateException("این قطعه یک نوبت جوشکاری باز دارد");
        }

        String displayName = resolveDisplayName(username);
        PieceWeldingDispatch dispatch = new PieceWeldingDispatch();
        dispatch.setPiece(piece);
        dispatch.setDefectType(storedType);
        dispatch.setDefectTypeCustom(storedCustom);
        dispatch.getDefects().addAll(selectedDefects);
        dispatch.setNotes(notes);
        dispatch.setSentBy(displayName);
        dispatch.setOriginStage(oldStage);
        dispatch.setOriginRadiographySubStage(piece.getRadiographySubStage());
        dispatch.setOriginHeatTreatmentSubStage(piece.getHeatTreatmentSubStage());
        dispatch.setOriginRoughMachiningSubStage(piece.getRoughMachiningSubStage());
        pieceWeldingDispatchRepository.save(dispatch);

        piece.setCurrentStage(PieceStage.WELDING);
        applyDefaultProcessDateOnMove(piece, oldStage);
        Piece saved = pieceRepository.save(piece);

        PieceStageHistory history = new PieceStageHistory();
        history.setPiece(saved);
        history.setStage(PieceStage.WELDING);
        history.setChangedBy(displayName);
        history = pieceStageHistoryRepository.save(history);

        return toResponse(
                saved,
                radiographyDefectRepository.countOpenByPieceIdAndStatus(saved.getId(), DefectStatus.NEEDS_WELDING),
                history.getChangedAt()
        );
    }

    public PieceWeldingDispatchResponse updateWeldingDispatch(
            UUID pieceId, UUID dispatchId, UpdateWeldingDispatchRequest request
    ) {
        Piece piece = pieceRepository.lockById(pieceId)
                .orElseThrow(() -> new ResourceNotFoundException("قطعه پیدا نشد"));
        assertNotArchived(piece);
        radiographyWorkflowGate.assertPieceNotInWorkflow(pieceId);
        PieceWeldingDispatch dispatch = pieceWeldingDispatchRepository
                .findFirstByPieceIdAndCompletedAtIsNullOrderBySentAtDesc(pieceId)
                .filter(open -> open.getId().equals(dispatchId))
                .orElseThrow(() -> new ResourceNotFoundException("نوبت باز جوشکاری پیدا نشد"));
        if (piece.getCurrentStage() != PieceStage.WELDING) {
            throw new IllegalStateException("ویرایش عیب فقط در ایستگاه جوشکاری امکان‌پذیر است");
        }
        Set<UUID> retainedDefectIds = dispatch.getDefects().stream().map(RadiographyDefect::getId).collect(Collectors.toSet());
        List<RadiographyDefect> defects = resolveOpenWeldingDefects(pieceId, request.getDefectIds(), retainedDefectIds);
        String storedType = DefectTypes.requireStoredType(request.getDefectType(), request.getDefectTypeCustom());
        dispatch.setDefectType(storedType);
        dispatch.setDefectTypeCustom(DefectTypes.storedCustom(storedType, request.getDefectTypeCustom()));
        dispatch.setNotes(request.getNotes() == null ? "" : request.getNotes().trim());
        dispatch.getDefects().clear();
        dispatch.getDefects().addAll(defects);
        return toWeldingDispatchResponse(pieceId, pieceWeldingDispatchRepository.save(dispatch));
    }

    public PieceResponse completeWeldingStation(UUID pieceId, CompleteWeldingRequest request, String username) {
        Piece piece = findPieceOrThrow(pieceId);
        assertNotArchived(piece);
        if (piece.getCurrentStage() != PieceStage.WELDING) {
            throw new IllegalStateException("فقط قطعات موجود در ایستگاه جوشکاری قابل اتمام هستند");
        }

        String displayName = resolveDisplayName(username);
        PieceWeldingDispatch openDispatch = pieceWeldingDispatchRepository
                .findFirstByPieceIdAndCompletedAtIsNullOrderBySentAtDesc(pieceId)
                .orElse(null);
        if (openDispatch != null) {
            openDispatch.setCompletedAt(LocalDateTime.now());
            openDispatch.setCompletedBy(displayName);
            confirmLinkedWeldingDefects(openDispatch);
            pieceWeldingDispatchRepository.save(openDispatch);
        }

        WeldingCompleteDestination destination = request.getDestination();
        PieceStage originStage = openDispatch != null ? openDispatch.getOriginStage() : null;
        boolean originBeforeShot = isOriginBeforeShot(originStage);
        boolean usePrevious = destination == WeldingCompleteDestination.PREVIOUS
                && originBeforeShot
                && originStage != null
                && originStage != PieceStage.WELDING;

        if (usePrevious) {
            return applyTrustedStageMove(
                    pieceId,
                    originStage,
                    openDispatch.getOriginRadiographySubStage(),
                    openDispatch.getOriginHeatTreatmentSubStage(),
                    openDispatch.getOriginRoughMachiningSubStage(),
                    username
            );
        }

        RadiographySubStage radioDest = destination == WeldingCompleteDestination.SHOT || originBeforeShot
                ? RadiographySubStage.SHOT
                : RadiographySubStage.INTERPRETATION;
        return applyTrustedStageMove(
                pieceId,
                PieceStage.RADIOGRAPHY,
                radioDest,
                null,
                null,
                username
        );
    }

    /** مبدأهایی که هنوز وارد رادیوگرافی/شوت نشده‌اند. */
    private boolean isOriginBeforeShot(PieceStage originStage) {
        return originStage == PieceStage.NOT_STARTED
                || originStage == PieceStage.CASTING
                || originStage == PieceStage.ROUGH_MACHINING;
    }

    @Transactional(readOnly = true)
    public List<PieceWeldingDispatchResponse> getWeldingDispatches(UUID pieceId) {
        findPieceOrThrow(pieceId);
        return pieceWeldingDispatchRepository.findByPieceIdOrderBySentAtDesc(pieceId).stream()
                .map(d -> toWeldingDispatchResponse(pieceId, d))
                .toList();
    }

    private PieceWeldingDispatchResponse toWeldingDispatchResponse(UUID pieceId, PieceWeldingDispatch d) {
        List<UUID> defectIds = new ArrayList<>();
        List<String> defectLabels = new ArrayList<>();
        String storedLabel = DefectTypes.displayLabel(d.getDefectType(), d.getDefectTypeCustom());
        defectLabels.add(storedLabel);
        for (RadiographyDefect defect : d.getDefects()) {
            defectIds.add(defect.getId());
            String label = DefectTypes.displayLabel(defect.getDefectType(), defect.getDefectTypeCustom());
            if (!defectLabels.contains(label)) {
                defectLabels.add(label);
            }
        }
        return new PieceWeldingDispatchResponse(
                d.getId(),
                pieceId,
                d.getDefectType(),
                d.getDefectTypeCustom(),
                d.getNotes(),
                d.getSentAt(),
                d.getSentBy(),
                d.getCompletedAt(),
                d.getCompletedBy(),
                d.isOpen(),
                defectIds,
                defectLabels,
                d.getOriginStage(),
                d.getOriginRadiographySubStage(),
                d.getOriginHeatTreatmentSubStage(),
                d.getOriginRoughMachiningSubStage()
        );
    }

    private List<RadiographyDefect> resolveOpenWeldingDefects(UUID pieceId, List<UUID> defectIds) {
        return resolveOpenWeldingDefects(pieceId, defectIds, Set.of());
    }

    private List<RadiographyDefect> resolveOpenWeldingDefects(UUID pieceId, List<UUID> defectIds, Set<UUID> retainedDefectIds) {
        if (defectIds == null || defectIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>(defectIds);
        List<RadiographyDefect> defects = radiographyDefectRepository.findByPieceIdAndIdIn(pieceId, uniqueIds);
        if (defects.size() != uniqueIds.size()) {
            throw new IllegalArgumentException("بعضی از عیب‌های انتخاب‌شده متعلق به این قطعه نیستند");
        }
        for (RadiographyDefect defect : defects) {
            if (!retainedDefectIds.contains(defect.getId())
                    && (defect.getStatus() != DefectStatus.NEEDS_WELDING || defect.isWeldingConfirmed())) {
                throw new IllegalStateException(
                        "فقط عیب‌های باز با وضعیت «نیاز به جوش» را می‌توان به ایستگاه جوشکاری فرستاد"
                );
            }
        }
        return defects;
    }

    private void confirmLinkedWeldingDefects(PieceWeldingDispatch dispatch) {
        List<RadiographyDefect> toConfirm = dispatch.getDefects().stream()
                .filter(d -> d.getStatus() == DefectStatus.NEEDS_WELDING && !d.isWeldingConfirmed())
                .toList();
        toConfirm.forEach(d -> d.setWeldingConfirmed(true));
        if (!toConfirm.isEmpty()) {
            radiographyDefectRepository.saveAll(toConfirm);
        }
    }

    /**
     * تاریخ انجام فرایند موقعیت قبلی را نگه می‌دارد و موقعیت جدید را خالی می‌گذارد
     * تا وقتی کاربر در ردیابی یا هنگام «مرحله بعد» آن را پر کند.
     */
    private void applyDefaultProcessDateOnMove(Piece piece, PieceStage oldStage) {
        if (oldStage != null && piece.getProcessDate() != null) {
            upsertStageProcessDate(piece, oldStage, piece.getProcessDate());
            upsertCurrentSubStageProcessDate(piece, oldStage, piece.getProcessDate());
        }
        piece.setProcessDate(null);
    }

    private void snapshotOutgoingProcessDate(Piece piece) {
        if (piece.getProcessDate() != null) {
            upsertStageProcessDate(piece, piece.getCurrentStage(), piece.getProcessDate());
            upsertCurrentSubStageProcessDate(piece, piece.getCurrentStage(), piece.getProcessDate());
        }
        piece.setProcessDate(null);
    }

    /** نوع زیرمرحله‌ی یک مرحله؛ برای مرحله‌های بدون زیرمرحله null است. */
    private static SubStageKind subStageKindOf(PieceStage stage) {
        if (stage == null) {
            return null;
        }
        return switch (stage) {
            case RADIOGRAPHY -> SubStageKind.RADIOGRAPHY;
            case HEAT_TREATMENT -> SubStageKind.HEAT_TREATMENT;
            case ROUGH_MACHINING -> SubStageKind.ROUGH_MACHINING;
            default -> null;
        };
    }

    /** زیرمرحله‌ی ثبت‌شده‌ی قطعه برای آن مرحله (با مقدار پیش‌فرض)؛ برای مرحله‌های بدون زیرمرحله null. */
    private static String subStageValueOf(Piece piece, PieceStage stage) {
        if (stage == null) {
            return null;
        }
        return switch (stage) {
            case RADIOGRAPHY -> StageTransitionPolicy.radioOf(piece).name();
            case HEAT_TREATMENT -> StageTransitionPolicy.heatOf(piece).name();
            case ROUGH_MACHINING -> StageTransitionPolicy.roughOf(piece).name();
            default -> null;
        };
    }

    /**
     * تاریخ انجام را برای زیرمرحله‌ی فعلیِ {@code stage} قطعه ذخیره می‌کند
     * (فقط وقتی آن مرحله زیرمرحله دارد).
     */
    private void upsertCurrentSubStageProcessDate(Piece piece, PieceStage stage, LocalDate date) {
        SubStageKind kind = subStageKindOf(stage);
        if (kind == null) {
            return;
        }
        upsertSubStageProcessDate(piece, kind, subStageValueOf(piece, stage), date);
    }

    private void upsertSubStageProcessDate(Piece piece, SubStageKind kind, String subStage, LocalDate date) {
        if (kind == null || subStage == null || subStage.isBlank()) {
            return;
        }
        var existing = pieceSubStageProcessDateRepository.findByPiece_IdAndKindAndSubStage(piece.getId(), kind, subStage);
        if (date == null) {
            existing.ifPresent(pieceSubStageProcessDateRepository::delete);
            return;
        }
        PieceSubStageProcessDate row = existing.orElseGet(PieceSubStageProcessDate::new);
        row.setPiece(piece);
        row.setKind(kind);
        row.setSubStage(subStage);
        row.setProcessDate(date);
        pieceSubStageProcessDateRepository.save(row);
    }

    private void upsertStageProcessDate(Piece piece, PieceStage stage, LocalDate date) {
        if (stage == null) {
            return;
        }
        var existing = pieceStageProcessDateRepository.findByPieceIdAndStage(piece.getId(), stage);
        if (date == null) {
            existing.ifPresent(pieceStageProcessDateRepository::delete);
            return;
        }
        PieceStageProcessDate row = existing.orElseGet(PieceStageProcessDate::new);
        row.setPiece(piece);
        row.setStage(stage);
        row.setProcessDate(date);
        pieceStageProcessDateRepository.save(row);
    }
}
