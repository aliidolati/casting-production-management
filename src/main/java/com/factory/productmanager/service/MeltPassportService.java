package com.factory.productmanager.service;

import com.factory.productmanager.exception.*;
import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.*;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class MeltPassportService {
    private final MeltPassportRepository passports;
    private final MeltPassportPieceRepository links;
    private final MeltMaterialRepository materials;
    private final PieceRepository pieces;
    private final UserRepository users;
    private final PieceService pieceService;
    private final Clock clock;
    private final Validator validator;
    private final MeltPassportAudit audit;
    private final MeltWorkflowTasks workflowTasks;
    private final TaskAccessPolicy taskPolicy;

    private boolean canEdit(MeltPassport m, User u) {
        return u.getRole() == Role.MANAGER || m.getAssignedOperatorId() == null || m.getAssignedOperatorId().equals(u.getId());
    }

    private void requireEditor(MeltPassport m, User u) {
        if (!canEdit(m, u)) throw new AccessDeniedException("فقط اپراتور تخصیص‌یافته یا ادمین می‌تواند شناسنامه را تکمیل کند");
    }

    public MeltPassportResponse assign(UUID id, MeltAssignmentRequest r, String username) {
        User u = actor(username);
        if (u.getRole() != Role.MANAGER) throw new AccessDeniedException("تخصیص اپراتور ذوب فقط توسط مدیر انجام می‌شود");
        var m = lock(id, r.version());
        assertEditable(m);
        var next = users.findById(r.assigneeId()).orElseThrow(() -> new ResourceNotFoundException("اپراتور پیدا نشد"));
        if (!taskPolicy.canReceiveTasks(next)) throw new IllegalArgumentException("اپراتور باید فعال و دارای دسترسی دریافت وظیفه باشد");
        if (next.getId().equals(m.getAssignedOperatorId())) return response(m, u);
        m.setAssignedOperatorId(next.getId()); m.setAssignedOperatorName(name(next));
        event(m, "OPERATOR_ASSIGNED", u, name(next));
        workflowTasks.assign(m, next, u);
        passports.flush();
        return response(m, u);
    }

    public MeltPassportResponse assignPiece(UUID pieceId, MeltAssignmentRequest r, String username) {
        User u = actor(username);
        if (u.getRole() != Role.MANAGER) throw new AccessDeniedException("تخصیص اپراتور ذوب فقط توسط مدیر انجام می‌شود");
        var existing = links.findByActivePieceKey(pieceId);
        if (existing.isPresent()) return assign(existing.get().getPassport().getId(), r, username);
        var request = new MeltPassportRequest(); request.setVersion(0L); request.setPieceIds(List.of(pieceId));
        var created = create(request, username);
        return assign(created.id(), new MeltAssignmentRequest(r.assigneeId(), created.version()), username);
    }

    // Piece APIs currently expose every piece to every authenticated, enabled user.
    // Keep this check central so future piece-scoping can be applied here as well.
    private User actor(String username) {
        return users.findByUsername(username).filter(u -> !u.isDisabled())
                .orElseThrow(() -> new AccessDeniedException("حساب کاربری فعال لازم است"));
    }

    private boolean reviewer(User u) {
        return u.getRole() == Role.MANAGER || u.getPermissions().contains(Permission.APPROVE_MELT_PASSPORT);
    }

    private void requireReviewer(User u) {
        if (!reviewer(u)) throw new AccessDeniedException("مجوز تأیید شناسنامه ذوب لازم است");
    }

    private void requireReportAccess(User u) {
        if (u.getRole() != Role.MANAGER && !u.getPermissions().contains(Permission.VIEW_DASHBOARD)) {
            throw new AccessDeniedException("مجوز مشاهده داشبورد برای گزارش شناسنامه‌های ذوب لازم است");
        }
    }

    private String name(User u) { return blank(u.getFullName()) ? u.getUsername() : u.getFullName(); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String trim(String s) { return blank(s) ? null : s.trim(); }

    private MeltPassport find(UUID id) {
        return passports.findById(id).orElseThrow(() -> new ResourceNotFoundException("شناسنامه ذوب پیدا نشد"));
    }

    private MeltPassport lock(UUID id, Long version) {
        var m = passports.lockById(id).orElseThrow(() -> new ResourceNotFoundException("شناسنامه ذوب پیدا نشد"));
        if (version == null || m.getVersion() != version) throw new DuplicateResourceException("نسخه شناسنامه تغییر کرده است؛ دوباره باز کنید");
        return m;
    }

    private List<Piece> lockPieces(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty() || ids.size() != new HashSet<>(ids).size()) {
            throw new IllegalArgumentException("حداقل یک قطعه و بدون تکرار انتخاب کنید");
        }
        return ids.stream().sorted().map(id -> pieces.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("قطعه پیدا نشد"))).toList();
    }

    private void assertCasting(Piece p) {
        if (p.getArchivedAt() != null || p.getCurrentStage() != PieceStage.CASTING) {
            throw new IllegalStateException("قطعه باید بایگانی‌نشده و در مرحله ذوب‌ریزی باشد");
        }
    }

    private void assertEditable(MeltPassport m) {
        if (m.getStatus() != MeltPassportStatus.DRAFT && m.getStatus() != MeltPassportStatus.NEEDS_CORRECTION) {
            throw new IllegalStateException("شناسنامه در انتظار تأیید یا تأییدشده قابل ویرایش نیست");
        }
        m.getPieces().forEach(l -> assertCasting(l.getPiece()));
    }

    public MeltPassportResponse create(MeltPassportRequest r, String username) {
        User u = actor(username);
        validate(r);
        var m = new MeltPassport();
        m.setNumber("MELT-" + UUID.randomUUID());
        m.setCreatedAt(LocalDateTime.now(clock));
        m.setCreatedById(u.getId()); m.setCreatedBy(name(u));
        if (r.getMeltDate() == null) r.setMeltDate(LocalDate.now(clock));
        apply(m, r);
        event(m, "CREATED", u, null);
        passports.saveAndFlush(m);
        // Direct creation remains available; the creator receives the entry task until ADMIN reassigns it.
        m.setAssignedOperatorId(u.getId()); m.setAssignedOperatorName(name(u));
        workflowTasks.assign(m, u, u);
        passports.flush();
        return response(m, u);
    }

    public MeltPassportResponse update(UUID id, MeltPassportRequest r, String username) {
        User u = actor(username);
        validate(r);
        var m = lock(id, r.getVersion());
        requireEditor(m, u);
        assertEditable(m);
        apply(m, r);
        workflowTasks.syncPiece(m);
        event(m, "UPDATED", u, null);
        passports.flush();
        return response(m, u);
    }

    public MeltPassportResponse correct(UUID id, MeltPassportCorrectionRequest request, String username) {
        User u = actor(username);
        if (u.getRole() != Role.MANAGER) throw new AccessDeniedException("فقط مدیر می‌تواند شناسنامه تأییدشده را اصلاح کند");
        var errors = validator.validate(request);
        if (!errors.isEmpty()) throw new IllegalArgumentException("اطلاعات اصلاح شناسنامه معتبر نیست: " + errors.iterator().next().getPropertyPath());
        var r = request.data();
        var m = lock(id, r.getVersion());
        if (m.getStatus() != MeltPassportStatus.APPROVED) throw new IllegalStateException("فقط شناسنامه تأییدشده از این مسیر اصلاح می‌شود");
        Set<UUID> originalIds = m.getPieces().stream().map(l -> l.getPiece().getId()).collect(Collectors.toSet());
        if (r.getPieceIds().size() != originalIds.size() || !originalIds.equals(new HashSet<>(r.getPieceIds()))) {
            throw new IllegalArgumentException("فهرست قطعات شناسنامه تأییدشده قابل تغییر نیست");
        }
        String before = audit.snapshot(m, magnesium(m));
        // Validate and compare on a detached candidate: a no-op must not replace child rows or bump the version.
        var candidate = new MeltPassport();
        candidate.setProcess(m.getProcess());
        candidate.getPieces().addAll(m.getPieces()); candidate.getMaterials().addAll(m.getMaterials());
        setMagnesium(candidate, magnesium(m));
        candidate.setMagnesiumResult(m.getMagnesiumResult()); candidate.setMagnesiumCalculatedAt(m.getMagnesiumCalculatedAt());
        applyFields(candidate, r);
        if (request.calculateMagnesium()) {
            candidate.setMagnesiumResult(MeltCalculations.magnesium(magnesium(candidate)));
            candidate.setMagnesiumCalculatedAt(LocalDateTime.now(clock));
        }
        validateSubmission(candidate);
        String after = audit.snapshot(candidate, magnesium(candidate));
        if (before.equals(after)) return response(m,u);
        applyFields(m,r);
        m.setMagnesiumResult(candidate.getMagnesiumResult()); m.setMagnesiumCalculatedAt(candidate.getMagnesiumCalculatedAt());
        var e = event(m,"ADMIN_CORRECTED",u,trim(request.reason()));
        e.setOldValues(before); e.setNewValues(after);
        passports.flush();
        return response(m,u);
    }

    private void validate(MeltPassportRequest r) {
        var errors = validator.validate(r);
        if (!errors.isEmpty()) throw new IllegalArgumentException("اطلاعات شناسنامه معتبر نیست: " + errors.iterator().next().getPropertyPath());
    }

    private void apply(MeltPassport m, MeltPassportRequest r) {
        // Lock both removed and added memberships in stable order.
        Set<UUID> all = m.getPieces().stream().map(l -> l.getPiece().getId()).collect(Collectors.toSet());
        all.addAll(r.getPieceIds());
        List<Piece> locked = lockPieces(all);
        if (r.getPieceIds().size() != new HashSet<>(r.getPieceIds()).size()) throw new IllegalArgumentException("قطعه تکراری است");
        locked.forEach(this::assertCasting);
        m.getPieces().removeIf(l -> !r.getPieceIds().contains(l.getPiece().getId()));
        for (UUID pieceId : r.getPieceIds()) {
            var existing = links.findByActivePieceKey(pieceId);
            if (existing.isPresent() && !existing.get().getPassport().getId().equals(m.getId())) {
                throw new DuplicateResourceException("قطعه قبلاً در یک شناسنامه ذوب ثبت شده است");
            }
            if (m.getPieces().stream().anyMatch(l -> l.getPiece().getId().equals(pieceId))) continue;
            Piece p = locked.stream().filter(x -> x.getId().equals(pieceId)).findFirst().orElseThrow();
            var link = new MeltPassportPiece();
            link.setPassport(m); link.setPiece(p); link.setActivePieceKey(pieceId);
            link.setPieceName(p.getPieceDefinition().getPieceName());
            var d = p.getPieceDefinition();
            link.setPieceRef(d.getProject().getCode() + "-" + d.getRowNum() + "-" + p.getPieceNumber());
            m.getPieces().add(link);
        }
        applyFields(m,r);
    }

    private void applyFields(MeltPassport m, MeltPassportRequest r) {
        m.setMeltDate(r.getMeltDate()); m.setFurnaceNumber(r.getFurnaceNumber() == null ? null : r.getFurnaceNumber().intValueExact());
        m.setIngotType(trim(r.getIngotType())); m.setPrimaryBatch(trim(r.getPrimaryBatch())); m.setRecycledBatch(trim(r.getRecycledBatch()));
        m.setPrimaryWeight(r.getPrimaryWeight()); m.setRecycledWeight(r.getRecycledWeight());
        m.setInjectionOperator(trim(r.getInjectionOperator())); m.setNotes(trim(r.getNotes())); m.setAdditionalNotes(trim(r.getAdditionalNotes()));
        m.setProcess(MeltProcessMapper.toEntity(r.getProcess(), m.getProcess()));
        setMagnesium(m, r.getMagnesium());
        replaceMaterials(m, r.getMaterials());
        replaceChecks(m, r.getChecks());
    }

    private void setMagnesium(MeltPassport m, MeltPassportRequest.MagnesiumInput v) {
        if (v == null) v = new MeltPassportRequest.MagnesiumInput();
        if (!same(m.getMg(), v.getMg()) || !same(m.getMgRotary(), v.getRotary()) || !same(m.getMg610(), v.getAt610())
                || !same(m.getMgAr(), v.getAr()) || !same(m.getMgB(), v.getB()) || !same(m.getMgW(), v.getW())) {
            m.setMagnesiumResult(null); m.setMagnesiumCalculatedAt(null);
        }
        m.setMg(v.getMg()); m.setMgRotary(v.getRotary()); m.setMg610(v.getAt610());
        m.setMgAr(v.getAr()); m.setMgB(v.getB()); m.setMgW(v.getW());
    }

    private boolean same(BigDecimal a, BigDecimal b) { return a == null ? b == null : b != null && a.compareTo(b) == 0; }

    private void replaceMaterials(MeltPassport m, List<MeltPassportRequest.MaterialLine> rows) {
        Map<UUID, MeltConsumption> previous = m.getMaterials().stream().collect(Collectors.toMap(MeltConsumption::getId, x -> x));
        // Catalog identity is stable; copied labels preserve previous usage after deactivation.
        Map<UUID, MeltConsumption> snapshots = previous.values().stream().collect(Collectors.toMap(x -> x.getMaterial().getId(), x -> x, (a,b) -> a));
        m.getMaterials().clear();
        int pos = 0;
        for (var row : rows) {
            MeltMaterial material = materials.findById(row.materialId()).orElseThrow(() -> new ResourceNotFoundException("ماده پیدا نشد"));
            var old = snapshots.get(material.getId());
            if (!material.isActive() && old == null) throw new IllegalArgumentException("ماده غیرفعال را نمی‌توان اضافه کرد");
            var line = new MeltConsumption();
            line.setPassport(m); line.setMaterial(material); line.setPosition(pos++);
            line.setMaterialName(old == null ? material.getName() : old.getMaterialName());
            line.setCategory(old == null ? material.getCategory() : old.getCategory());
            line.setQuantity(row.quantity()); line.setUnit(row.unit());
            BigDecimal factor = switch (row.unit()) {
                case G, ML -> new BigDecimal("0.001");
                case TON -> new BigDecimal("1000");
                default -> BigDecimal.ONE;
            };
            line.setBaseQuantity(row.quantity() == null ? null : row.quantity().multiply(factor));
            line.setBaseUnit(switch (row.unit()) { case KG, G, TON -> "KG"; case L, ML -> "L"; case COUNT -> "COUNT"; });
            line.setAddedAt(row.addedAt()); line.setBatchNumber(trim(row.batchNumber())); line.setNotes(trim(row.notes()));
            m.getMaterials().add(line);
        }
    }

    private void replaceChecks(MeltPassport m, List<MeltPassportRequest.CheckLine> rows) {
        Map<MeltCheckKind, MeltPassportRequest.CheckLine> byKind = new EnumMap<>(MeltCheckKind.class);
        for (var r : rows) if (byKind.put(r.kind(), r) != null) throw new IllegalArgumentException("مورد کنترلی تکراری است");
        for (var kind : MeltCheckKind.values()) {
            var line = m.getChecks().stream().filter(c -> c.getKind() == kind).findFirst().orElseGet(() -> {
                var added = new MeltCheck(); added.setPassport(m); added.setKind(kind); m.getChecks().add(added); return added;
            });
            var r = byKind.get(kind);
            line.setStatus(r == null ? MeltCheckStatus.UNCHECKED : r.status());
            line.setNotes(r == null ? null : trim(r.notes()));
        }
    }

    public MeltPassportResponse calculate(UUID id, MeltDecisionRequest r, String username) {
        User u = actor(username);
        var m = lock(id, r.version()); requireEditor(m, u); assertEditable(m);
        m.setMagnesiumResult(MeltCalculations.magnesium(magnesium(m)));
        m.setMagnesiumCalculatedAt(LocalDateTime.now(clock));
        event(m, "MAGNESIUM_CALCULATED", u, null); passports.flush();
        return response(m, u);
    }

    public MeltPassportResponse submit(UUID id, MeltDecisionRequest r, String username) {
        User u = actor(username);
        var m = lock(id, r.version()); requireEditor(m, u); assertEditable(m);
        lockPieces(m.getPieces().stream().map(l -> l.getPiece().getId()).toList()).forEach(this::assertCasting);
        validateSubmission(m);
        if (m.getAssignedOperatorId() == null) {
            m.setAssignedOperatorId(u.getId()); m.setAssignedOperatorName(name(u));
            workflowTasks.assign(m, u, u);
        }
        m.setStatus(MeltPassportStatus.PENDING_APPROVAL);
        m.setSubmittedAt(LocalDateTime.now(clock)); m.setSubmittedById(u.getId()); m.setSubmittedBy(name(u));
        event(m, "SUBMITTED", u, null); passports.flush();
        workflowTasks.submitted(m, u);
        return response(m, u);
    }

    private void validateSubmission(MeltPassport m) {
        if (m.getPieces().isEmpty() || m.getMeltDate() == null || m.getFurnaceNumber() == null || blank(m.getIngotType()) || blank(m.getInjectionOperator())
                || m.getPrimaryWeight() == null || m.getRecycledWeight() == null
                || m.getPrimaryWeight().add(m.getRecycledWeight()).signum() <= 0) {
            throw new IllegalArgumentException("تاریخ، کوره، نوع شمش، هر دو وزن با مجموع مثبت و نام اپراتور برای ارسال لازم است");
        }
        for (var c : m.getChecks()) {
            if (c.getStatus() == MeltCheckStatus.UNCHECKED || (c.getStatus() == MeltCheckStatus.REJECTED && blank(c.getNotes()))) {
                throw new IllegalArgumentException("تمام موارد کنترلی را بررسی کنید و برای موارد ردشده توضیح بنویسید");
            }
        }
        if (m.getMaterials().stream().anyMatch(x -> x.getQuantity() == null || x.getQuantity().signum() <= 0)) throw new IllegalArgumentException("مقدار مصرف مواد باید مثبت باشد");
        boolean anyMg = Arrays.asList(m.getMg(), m.getMgRotary(), m.getMg610(), m.getMgAr(), m.getMgB(), m.getMgW()).stream().anyMatch(Objects::nonNull);
        if (anyMg && m.getMagnesiumResult() == null) throw new IllegalArgumentException("محاسبه منیزیم را تکمیل و دوباره محاسبه کنید یا هر شش ورودی را پاک کنید");
    }

    public MeltPassportResponse approve(UUID id, MeltDecisionRequest r, String username) {
        User u = actor(username); requireReviewer(u);
        var m = lock(id, r.version()); requirePending(m); validateSubmission(m);
        List<Piece> locked = lockPieces(m.getPieces().stream().map(l -> l.getPiece().getId()).toList());
        locked.forEach(this::assertCasting);
        for (var p : locked) {
            var active = links.findByActivePieceKey(p.getId());
            if (active.isEmpty() || !active.get().getPassport().getId().equals(id)) throw new IllegalStateException("ارتباط فعال قطعه با شناسنامه تغییر کرده است");
        }
        m.setStatus(MeltPassportStatus.APPROVED);
        m.setApprovedAt(LocalDateTime.now(clock)); m.setApprovedById(u.getId()); m.setApprovedBy(name(u));
        event(m, "APPROVED", u, null);
        // Flush approval before central departure guards. Everything still rolls back on failure.
        passports.flush();
        for (Piece p : locked) pieceService.advanceByMeltApproval(p.getId(), m.getMeltDate(), username);
        pieces.flush();
        workflowTasks.decided(m, u, true);
        return response(m, u);
    }

    public MeltPassportResponse cancel(UUID id, MeltDecisionRequest r, String username) {
        User u=actor(username);
        if(u.getRole()!=Role.MANAGER && !u.getPermissions().contains(Permission.CANCEL_MELT_PASSPORT)) throw new AccessDeniedException("مجوز لغو شناسنامه ذوب لازم است");
        var m=lock(id,r.version());
        if(m.getStatus()==MeltPassportStatus.APPROVED || m.getStatus()==MeltPassportStatus.CANCELLED) throw new IllegalStateException("شناسنامه تأییدشده یا لغوشده قابل لغو نیست");
        if(blank(r.reason())) throw new IllegalArgumentException("دلیل لغو لازم است");
        var locked=lockPieces(m.getPieces().stream().map(l->l.getPiece().getId()).toList());
        locked.forEach(this::assertCasting);
        for(var link:m.getPieces()) {
            if(!link.getPiece().getId().equals(link.getActivePieceKey())) throw new IllegalStateException("ارتباط فعال قطعه تغییر کرده است");
        }
        String before=m.getStatus().name();
        m.setStatus(MeltPassportStatus.CANCELLED);
        m.getPieces().forEach(l->l.setActivePieceKey(null));
        var e=event(m,"CANCELLED",u,r.reason().trim());e.setOldValues("{\"status\":\""+before+"\"}");e.setNewValues("{\"status\":\"CANCELLED\"}");
        workflowTasks.cancelled(m,u,r.reason().trim());passports.flush();
        return response(m,u);
    }

    public MeltPassportResponse returnForCorrection(UUID id, MeltDecisionRequest r, String username) {
        User u = actor(username); requireReviewer(u);
        var m = lock(id, r.version()); requirePending(m);
        if (blank(r.reason())) throw new IllegalArgumentException("دلیل بازگشت برای اصلاح الزامی است");
        m.setStatus(MeltPassportStatus.NEEDS_CORRECTION); m.setCorrectionReason(r.reason().trim());
        m.setReturnedAt(LocalDateTime.now(clock)); m.setReturnedById(u.getId()); m.setReturnedBy(name(u));
        event(m, "RETURNED", u, r.reason().trim()); passports.flush();
        workflowTasks.decided(m, u, false);
        return response(m, u);
    }

    private void requirePending(MeltPassport m) {
        if (m.getStatus() != MeltPassportStatus.PENDING_APPROVAL) throw new IllegalStateException("فقط شناسنامه در انتظار تأیید قابل بررسی است");
    }

    private MeltPassportEvent event(MeltPassport m, String action, User u, String reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        // Child-only changes must also dirty the versioned parent, including with a fixed/coarse clock.
        if (m.getUpdatedAt() != null && !now.isAfter(m.getUpdatedAt())) now = m.getUpdatedAt().plusNanos(1000);
        m.setUpdatedAt(now); m.setUpdatedById(u.getId()); m.setUpdatedBy(name(u));
        var e = new MeltPassportEvent(); e.setPassport(m); e.setAction(action);
        e.setActorId(u.getId()); e.setActorName(name(u)); e.setOccurredAt(now); e.setReason(reason);
        m.getEvents().add(e);
        return e;
    }

    @Transactional(readOnly=true)
    public MeltPassportResponse get(UUID id, String username) { return response(find(id), actor(username)); }

    @Transactional(readOnly=true)
    public List<MeltPassportResponse> forPiece(UUID id, String username) {
        User u = actor(username); pieceService.findPieceOrThrow(id);
        return links.findByPiece_IdOrderByPassport_CreatedAtDesc(id).stream().map(l -> response(l.getPassport(), u)).toList();
    }

    @Transactional(readOnly=true)
    public PageResponse<MeltPassportResponse> search(MeltPassportFilter filter, int page, int size, String username) {
        User u = actor(username);
        requireReportAccess(u);
        if (filter.getStatus() == MeltPassportStatus.PENDING_APPROVAL) requireReviewer(u);
        var result = passports.findAll(MeltPassportSpecifications.matching(filter), PageRequest.of(Math.max(0,page), Math.max(1,Math.min(100,size)), Sort.by(Sort.Direction.DESC,"createdAt")));
        return PageResponse.from(result,m -> response(m,u));
    }

    @Transactional(readOnly=true)
    public List<MeltPassportResponse.PieceLink> candidates(String keyword, String username) {
        actor(username);
        var search = MeltCandidateSearch.parse(keyword);
        String pattern = "%" + search.keyword().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return pieces.lookupMeltCandidates(search.keyword().isEmpty(), search.numeric(), search.pieceNumber(), pattern,
                        search.projectCode() == null ? null : search.projectCode().toLowerCase(Locale.ROOT),
                        search.rowNumber(), search.referencePieceNumber(), PageRequest.of(0,100)).stream()
                .map(p -> new MeltPassportResponse.PieceLink(p.getId(), p.getPieceDefinition().getPieceName(),
                        p.getPieceDefinition().getProject().getCode()+"-"+p.getPieceDefinition().getRowNum()+"-"+p.getPieceNumber()))
                .toList();
    }

    @Transactional(readOnly=true)
    public List<String> ingotTypes(String username) { actor(username); return passports.ingotTypes(); }

    @Transactional(readOnly=true)
    public List<MeltMaterialResponse> materialList(String username) {
        actor(username); return materials.findAllByOrderByNameAsc().stream().map(this::materialResponse).toList();
    }

    private final RecordEditService recordEditService;

    public MeltMaterialResponse createMaterial(MeltMaterialRequest r, String username) {
        User catalogActor=actor(username);
        if(catalogActor.getRole()!=Role.MANAGER && !catalogActor.getPermissions().contains(Permission.MANAGE_MELT_MATERIALS)) throw new AccessDeniedException("مجوز مدیریت کاتالوگ مواد لازم است");
        String normalized = Normalizer.normalize(r.name(), Normalizer.Form.NFKC).replace('ي','ی').replace('ك','ک')
                .replace('\u200c',' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("نام ماده لازم است");
        var existing = materials.findByNormalizedName(normalized);
        if (existing.isPresent()) return materialResponse(existing.get());
        var material = new MeltMaterial(); material.setName(r.name().trim()); material.setNormalizedName(normalized); material.setCategory(r.category().trim());
        return materialResponse(materials.saveAndFlush(material));
    }

    public MeltMaterialResponse setMaterialActive(UUID id, boolean active, String username) {
        return recordEditService.activeMaterial(id,active,username);
    }

    private MeltMaterialResponse materialResponse(MeltMaterial m) { return new MeltMaterialResponse(m.getId(),m.getName(),m.getCategory(),m.isActive()); }

    @Transactional(readOnly=true)
    public List<MeltMaterialTotal> consumption(MeltPassportFilter f, String username) {
        User u = actor(username);
        requireReportAccess(u);
        if (f.getStatus() == MeltPassportStatus.PENDING_APPROVAL) requireReviewer(u);
        if (f.getStatus() == null) f.setStatus(MeltPassportStatus.APPROVED);
        var melts = passports.findAll(MeltPassportSpecifications.matching(f));
        record Key(UUID materialId, String unit) {}
        Map<Key,BigDecimal> totals = new LinkedHashMap<>();
        Map<Key,Set<UUID>> meltIds = new HashMap<>();
        Map<Key,String> names = new HashMap<>();
        // No join to pieces: every consumption line is counted exactly once per melt.
        for (var m : melts) if(m.getStatus()!=MeltPassportStatus.CANCELLED) for (var line : m.getMaterials()) {
            if (f.getMaterialId() != null && !f.getMaterialId().equals(line.getMaterial().getId())) continue;
            var key = new Key(line.getMaterial().getId(),line.getBaseUnit());
            if (line.getBaseQuantity() == null) continue;
            totals.merge(key,line.getBaseQuantity(),BigDecimal::add);
            meltIds.computeIfAbsent(key,k -> new HashSet<>()).add(m.getId());
            names.putIfAbsent(key,line.getMaterialName());
        }
        return totals.entrySet().stream().map(e -> new MeltMaterialTotal(e.getKey().materialId(),names.get(e.getKey()),e.getKey().unit(),e.getValue(),meltIds.get(e.getKey()).size())).toList();
    }

    private MeltPassportRequest.MagnesiumInput magnesium(MeltPassport m) {
        var v = new MeltPassportRequest.MagnesiumInput();
        v.setMg(m.getMg()); v.setRotary(m.getMgRotary()); v.setAt610(m.getMg610()); v.setAr(m.getMgAr()); v.setB(m.getMgB()); v.setW(m.getMgW()); return v;
    }

    private MeltPassportResponse response(MeltPassport m, User u) {
        boolean editable = canEdit(m,u) && (m.getStatus() == MeltPassportStatus.DRAFT || m.getStatus() == MeltPassportStatus.NEEDS_CORRECTION)
                && m.getPieces().stream().allMatch(l -> l.getPiece().getArchivedAt() == null && l.getPiece().getCurrentStage() == PieceStage.CASTING);
        return new MeltPassportResponse(m.getId(),m.getVersion(),m.getNumber(),m.getStatus(),m.getMeltDate(),m.getFurnaceNumber(),m.getIngotType(),m.getPrimaryBatch(),m.getRecycledBatch(),
                m.getPrimaryWeight(),m.getRecycledWeight(),MeltCalculations.percent(m.getPrimaryWeight(),m.getRecycledWeight()),MeltCalculations.percent(m.getRecycledWeight(),m.getPrimaryWeight()),
                m.getInjectionOperator(),m.getNotes(),m.getAdditionalNotes(),MeltProcessMapper.toData(m.getProcess()),magnesium(m),m.getMagnesiumResult(),m.getMagnesiumCalculatedAt(),
                m.getPieces().stream().map(l -> new MeltPassportResponse.PieceLink(l.getPiece().getId(),l.getPieceName(),l.getPieceRef())).toList(),
                m.getMaterials().stream().map(l -> new MeltPassportResponse.MaterialLine(l.getMaterial().getId(),l.getMaterialName(),l.getCategory(),l.getQuantity(),l.getUnit(),l.getBaseQuantity(),l.getBaseUnit(),l.getAddedAt(),l.getBatchNumber(),l.getNotes())).toList(),
                m.getChecks().stream().map(c -> new MeltPassportRequest.CheckLine(c.getKind(),c.getStatus(),c.getNotes())).toList(),
                m.getEvents().stream().map(e -> new MeltPassportResponse.Event(e.getAction(),e.getActorId(),e.getActorName(),e.getOccurredAt(),e.getReason(),audit.read(e.getOldValues()),audit.read(e.getNewValues()))).toList(),
                m.getCreatedAt(),m.getCreatedBy(),m.getUpdatedAt(),m.getUpdatedBy(),m.getSubmittedAt(),m.getSubmittedBy(),m.getApprovedAt(),m.getApprovedBy(),m.getReturnedAt(),m.getReturnedBy(),m.getCorrectionReason(),editable,
                reviewer(u) && m.getStatus() == MeltPassportStatus.PENDING_APPROVAL,
                u.getRole() == Role.MANAGER && m.getStatus() == MeltPassportStatus.APPROVED,
                m.getAssignedOperatorId(), m.getAssignedOperatorName(), u.getRole() == Role.MANAGER && editable,
                (u.getRole()==Role.MANAGER || u.getPermissions().contains(Permission.CANCEL_MELT_PASSPORT))
                 && m.getStatus()!=MeltPassportStatus.APPROVED && m.getStatus()!=MeltPassportStatus.CANCELLED);
    }
}
