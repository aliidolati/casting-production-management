package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.BulkArchiveResultResponse;
import com.factory.productmanager.model.dto.PieceDefinitionRequest;
import com.factory.productmanager.model.dto.PieceDefinitionResponse;
import com.factory.productmanager.model.dto.RowPieceStatusReportResponse;
import com.factory.productmanager.model.dto.UpdatePieceDefinitionRequest;
import com.factory.productmanager.service.PieceDefinitionService;
import com.factory.productmanager.service.PieceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/piece-definitions")
@RequiredArgsConstructor
public class PieceDefinitionController {

    private final PieceDefinitionService pieceDefinitionService;
    private final PieceService pieceService;

    @PostMapping
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECE_DEFINITIONS')")
    public ResponseEntity<PieceDefinitionResponse> createDefinition(
            @Valid @RequestBody PieceDefinitionRequest request
    ) {
        PieceDefinitionResponse response = pieceDefinitionService.createPieceDefinition(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<PieceDefinitionResponse>> getDefinitionsByProject(
            @RequestParam UUID projectId
    ) {
        return ResponseEntity.ok(pieceDefinitionService.getDefinitionsByProject(projectId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PieceDefinitionResponse> getDefinitionById(@PathVariable UUID id) {
        return ResponseEntity.ok(pieceDefinitionService.getDefinitionById(id));
    }

    @GetMapping("/{id}/status-report")
    public ResponseEntity<RowPieceStatusReportResponse> getStatusReport(@PathVariable UUID id) {
        return ResponseEntity.ok(pieceService.getRowStatusReport(id));
    }

    @PostMapping("/{id}/archive-pieces")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECES')")
    public ResponseEntity<BulkArchiveResultResponse> archivePieces(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pieceService.archivePiecesByDefinition(id, authentication.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECE_DEFINITIONS')")
    public ResponseEntity<PieceDefinitionResponse> updateDefinition(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePieceDefinitionRequest request
    ) {
        return ResponseEntity.ok(pieceDefinitionService.updateDefinition(id, request));
    }

    @PostMapping(value = "/{id}/drawing", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECE_DEFINITIONS')")
    public ResponseEntity<PieceDefinitionResponse> uploadDrawing(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(pieceDefinitionService.uploadDrawing(id, file));
    }

    @GetMapping("/{id}/drawing")
    public ResponseEntity<Resource> getDrawing(@PathVariable UUID id) {
        Resource resource = pieceDefinitionService.loadDrawingResource(id);
        MediaType mediaType = pieceDefinitionService.drawingMediaType(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .contentType(mediaType)
                .body(resource);
    }

    @DeleteMapping("/{id}/drawing")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECE_DEFINITIONS')")
    public ResponseEntity<PieceDefinitionResponse> deleteDrawing(@PathVariable UUID id) {
        return ResponseEntity.ok(pieceDefinitionService.deleteDrawing(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECE_DEFINITIONS')")
    public ResponseEntity<Void> deleteDefinition(@PathVariable UUID id) {
        pieceDefinitionService.deleteDefinition(id);
        return ResponseEntity.noContent().build();
    }
}
