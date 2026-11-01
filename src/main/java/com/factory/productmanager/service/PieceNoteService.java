package com.factory.productmanager.service;

import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.PieceNoteRequest;
import com.factory.productmanager.model.dto.PieceNoteResponse;
import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.PieceNote;
import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.repository.PieceNoteRepository;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class PieceNoteService {

    private static final String LEGACY_NOTE_AUTHOR = "سیستم (توضیحات قبلی)";

    private final PieceNoteRepository pieceNoteRepository;
    private final PieceService pieceService;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    /**
     * نام قابل‌نمایش کاربر (نام و نام خانوادگی؛ در صورت نبود، نام کاربری) را برای ثبت روی یادداشت برمی‌گرداند.
     */
    private String resolveDisplayName(String username) {
        return userRepository.findByUsername(username)
                .map(u -> (u.getFullName() != null && !u.getFullName().isBlank()) ? u.getFullName() : u.getUsername())
                .orElse(username);
    }

    public PieceNoteResponse createNote(UUID pieceId, PieceNoteRequest request, String createdByUsername) {
        Piece piece = pieceService.findPieceOrThrow(pieceId);

        PieceNote note = new PieceNote();
        note.setPiece(piece);
        note.setContent(request.getContent());
        note.setCreatedBy(resolveDisplayName(createdByUsername));
        note.setCreatedByUsername(createdByUsername);

        PieceNote saved = pieceNoteRepository.save(note);
        return toResponse(saved, createdByUsername);
    }

    /**
     * یادداشت‌های یک قطعه را برمی‌گرداند. اگر قطعه هنوز هیچ یادداشتی نداشته باشد ولی فیلد قدیمی
     * description مقدار داشته باشد، آن را یک‌بار به‌صورت یک یادداشت ثبت می‌کند تا داده‌ی قدیمی از دست نرود.
     * پارامتر requestingUsername فقط برای محاسبه‌ی اینکه کاربر جاری اجازه‌ی حذف کدام یادداشت‌ها را دارد استفاده می‌شود.
     */
    public List<PieceNoteResponse> getNotesByPiece(UUID pieceId, String requestingUsername) {
        Piece piece = pieceService.findPieceOrThrow(pieceId);
        List<PieceNote> notes = pieceNoteRepository.findByPieceIdOrderByCreatedAtDesc(pieceId);

        if (notes.isEmpty() && piece.getDescription() != null && !piece.getDescription().isBlank()) {
            PieceNote legacyNote = new PieceNote();
            legacyNote.setPiece(piece);
            legacyNote.setContent(piece.getDescription());
            legacyNote.setCreatedBy(LEGACY_NOTE_AUTHOR);
            notes = List.of(pieceNoteRepository.save(legacyNote));
        }

        return notes.stream().map(n -> toResponse(n, requestingUsername)).toList();
    }

    public void deleteNote(UUID id, String requestingUsername) {
        PieceNote note = pieceNoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("یادداشتی با شناسه " + id + " پیدا نشد"));

        if (!canDelete(note, requestingUsername)) {
            throw new AccessDeniedException("فقط نویسنده‌ی یادداشت یا مدیر می‌تواند آن را حذف کند");
        }

        pieceNoteRepository.delete(note);
    }

    /**
     * ادمین همیشه می‌تواند حذف کند؛ اپراتور فقط یادداشت خودش را و فقط با دسترسی MANAGE_PIECE_NOTES.
     * یادداشت‌های مهاجرت‌شده از توضیحات قدیمی نویسنده‌ی مشخصی ندارند، پس فقط ادمین می‌تواند حذفشان کند.
     */
    private boolean canDelete(PieceNote note, String requestingUsername) {
        if (requestingUsername == null) return false;

        if (permissionService.isManager(requestingUsername)) {
            return true;
        }

        boolean isOwner = requestingUsername.equals(note.getCreatedByUsername());
        boolean hasNotePermission = permissionService.hasPermission(requestingUsername, Permission.MANAGE_PIECE_NOTES);

        return isOwner && hasNotePermission;
    }

    private PieceNoteResponse toResponse(PieceNote note, String requestingUsername) {
        return new PieceNoteResponse(
                note.getId(),
                note.getContent(),
                note.getCreatedAt(),
                note.getCreatedBy(),
                canDelete(note, requestingUsername)
        );
    }
}
