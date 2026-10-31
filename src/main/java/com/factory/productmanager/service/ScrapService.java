package com.factory.productmanager.service;

import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.*;
import com.factory.productmanager.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class ScrapService {
    private final PieceRepository pieces;
    private final PieceScrapDispatchRepository dispatches;
    private final RadiographyDefectRepository defects;
    private final PieceStageHistoryRepository histories;
    private final UserRepository users;

    private String actor(String username) {
        return users.findByUsername(username).map(u -> u.getFullName() == null || u.getFullName().isBlank()
            ? u.getUsername() : u.getFullName()).orElse(username);
    }
    private void setReason(PieceScrapDispatch d, ScrapRequest request) {
        if (request == null) throw new IllegalArgumentException("برای اسقاط، علت را انتخاب کنید");
        String type = DefectTypes.requireStoredType(request.getDefectType(), request.getDefectTypeCustom());
        String custom = DefectTypes.storedCustom(type, request.getDefectTypeCustom());
        if (custom != null && custom.length() > 255) throw new IllegalArgumentException("متن علت حداکثر ۲۵۵ کاراکتر است");
        String notes = request.getNotes() == null ? null : request.getNotes().trim();
        if (notes != null && notes.length() > 10000) throw new IllegalArgumentException("توضیحات حداکثر ۱۰۰۰۰ کاراکتر است");
        var ids = new LinkedHashSet<>(request.getDefectIds() == null ? List.<UUID>of() : request.getDefectIds());
        if (ids.contains(null)) throw new IllegalArgumentException("شناسه عیب معتبر نیست");
        var selected = ids.isEmpty() ? List.<RadiographyDefect>of() : defects.findByPieceIdAndIdIn(d.getPiece().getId(), ids);
        if (selected.size() != ids.size()) throw new IllegalArgumentException("عیب انتخاب‌شده متعلق به این قطعه نیست");
        d.setDefectType(type); d.setDefectTypeCustom(custom); d.setNotes(notes);
        d.getDefects().clear(); d.getDefects().addAll(selected);
    }
    /** Called only by the stage boundary, in the SAME transaction as the piece and stage history. */
    public void record(Piece piece, ScrapRequest request, String username) {
        if (dispatches.findByActivePieceKey(piece.getId()).isPresent())
            throw new IllegalStateException("این قطعه یک رکورد اسقاط فعال دارد");
        var d = new PieceScrapDispatch(); d.setPiece(piece); d.setActivePieceKey(piece.getId());
        d.setOriginStage(piece.getCurrentStage());
        d.setOriginSubStage(switch (piece.getCurrentStage()) {
            case RADIOGRAPHY -> StageTransitionPolicy.radioOf(piece).name();
            case HEAT_TREATMENT -> StageTransitionPolicy.heatOf(piece).name();
            case ROUGH_MACHINING -> StageTransitionPolicy.roughOf(piece).name();
            default -> null;
        });
        setReason(d, request);
        d.setSentAt(LocalDateTime.now()); d.setSentBy(actor(username));
        d.setUpdatedAt(d.getSentAt()); d.setUpdatedBy(d.getSentBy());
        dispatches.save(d);
    }
    public void reverse(UUID pieceId, String username) {
        dispatches.findByActivePieceKey(pieceId).ifPresent(d -> {
            d.setActivePieceKey(null); d.setReversedAt(LocalDateTime.now()); d.setReversedBy(actor(username));
            dispatches.saveAndFlush(d); // Release the unique active key before another cycle can start.
        });
    }
    public ScrapDispatchResponse edit(UUID pieceId, ScrapRequest request, String username) {
        var piece = pieces.lockById(pieceId).orElseThrow(() -> new ResourceNotFoundException("قطعه پیدا نشد"));
        if (piece.getArchivedAt() != null) throw new IllegalStateException("برای ویرایش ابتدا قطعه را از بایگانی بازگردانید");
        if (piece.getCurrentStage() != PieceStage.SCRAPPED) throw new IllegalStateException("ویرایش علت فقط برای قطعه اسقاطی مجاز است");
        var d = dispatches.findByActivePieceKey(pieceId).orElse(null);
        if (d != null && (request == null || request.getVersion() == null || request.getVersion() != d.getVersion()))
            throw new ObjectOptimisticLockingFailureException(PieceScrapDispatch.class, d.getId());
        if (d == null) {
            d = new PieceScrapDispatch(); d.setPiece(piece); d.setActivePieceKey(pieceId);
            var last = histories.findByPieceIdOrderByChangedAtDesc(pieceId).stream()
                .filter(h -> h.getStage() == PieceStage.SCRAPPED).findFirst();
            if (last.isPresent()) { d.setSentAt(last.get().getChangedAt()); d.setSentBy(last.get().getChangedBy()); }
        }
        setReason(d, request); d.setUpdatedAt(LocalDateTime.now()); d.setUpdatedBy(actor(username));
        return response(piece, dispatches.saveAndFlush(d));
    }
    @Transactional(readOnly=true)
    public List<ScrapDispatchResponse> history(UUID pieceId) {
        var p = pieces.findById(pieceId).orElseThrow(() -> new ResourceNotFoundException("قطعه پیدا نشد"));
        return dispatches.findByPieceIdOrderBySentAtDesc(pieceId).stream().map(d -> response(p,d)).toList();
    }
    @Transactional(readOnly=true)
    public List<ScrapDispatchResponse> report(UUID projectId) {
        var selected = pieces.findScrappedIncludingArchived(projectId);
        if (selected.isEmpty()) return List.of();
        Map<UUID,PieceScrapDispatch> active = new HashMap<>();
        dispatches.findByActivePieceKeyIn(selected.stream().map(Piece::getId).toList())
            .forEach(d -> active.put(d.getPiece().getId(),d));
        Map<UUID,PieceStageHistory> legacyHistory = new HashMap<>();
        var missing = selected.stream().map(Piece::getId).filter(id -> !active.containsKey(id)).toList();
        if (!missing.isEmpty()) histories.findByPiece_IdInAndStageOrderByChangedAtAsc(missing,PieceStage.SCRAPPED)
            .forEach(h -> legacyHistory.put(h.getPiece().getId(),h));
        return selected.stream().map(p -> response(p,active.get(p.getId()),legacyHistory.get(p.getId())))
            .sorted(Comparator.comparing(ScrapDispatchResponse::sentAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ScrapDispatchResponse::pieceId)).toList();
    }
    private ScrapDispatchResponse response(Piece p, PieceScrapDispatch d) {
        return response(p,d,null);
    }
    private ScrapDispatchResponse response(Piece p, PieceScrapDispatch d, PieceStageHistory legacy) {
        return new ScrapDispatchResponse(d == null ? null : d.getId(), d == null ? 0 : d.getVersion(),
            p.getId(),p.getPieceDefinition().getProject().getCode(),p.getPieceDefinition().getPieceName(),p.getPieceNumber(),
            d == null ? null : d.getDefectType(),d == null ? null : d.getDefectTypeCustom(),
            d == null ? "ثبت نشده" : DefectTypes.displayLabel(d.getDefectType(),d.getDefectTypeCustom()),
            d == null ? null : d.getNotes(),d == null ? (legacy == null ? null : legacy.getChangedAt()) : d.getSentAt(),
            d == null ? (legacy == null ? null : legacy.getChangedBy()) : d.getSentBy(),
            d == null ? null : d.getOriginStage(),d == null ? null : d.getOriginSubStage(),
            d == null ? null : d.getReversedAt(),d == null ? null : d.getReversedBy(),
            d == null ? null : d.getUpdatedAt(),d == null ? null : d.getUpdatedBy(),p.getArchivedAt()!=null,
            d == null || d.getActivePieceKey()!=null,d == null ? List.of() : d.getDefects().stream().map(RadiographyDefect::getId).toList());
    }
}
