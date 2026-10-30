package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.PieceNoteRequest;
import com.factory.productmanager.model.dto.PieceNoteResponse;
import com.factory.productmanager.service.PieceNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PieceNoteController {

    private final PieceNoteService pieceNoteService;

    // ثبت یادداشت کاری است که هم اپراتور و هم ادمین باید بتوانند انجام دهند (مثل توضیحات قبلی)
    @PostMapping("/api/pieces/{pieceId}/notes")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECE_NOTES')")
    public ResponseEntity<PieceNoteResponse> createNote(
            @PathVariable UUID pieceId,
            @Valid @RequestBody PieceNoteRequest request,
            Authentication authentication
    ) {
        PieceNoteResponse response = pieceNoteService.createNote(pieceId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/pieces/{pieceId}/notes")
    public ResponseEntity<List<PieceNoteResponse>> getNotesByPiece(
            @PathVariable UUID pieceId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pieceNoteService.getNotesByPiece(pieceId, authentication.getName()));
    }

    // حذف یادداشت یعنی از بین رفتن بخشی از تاریخچه؛ فقط خودِ نویسنده یا ادمین مجازند
    // (بررسی دقیق مالکیت داخل سرویس انجام می‌شود چون نیاز به مقایسه با نویسنده‌ی یادداشت دارد)
    @DeleteMapping("/api/notes/{id}")
    public ResponseEntity<Void> deleteNote(@PathVariable UUID id, Authentication authentication) {
        pieceNoteService.deleteNote(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
