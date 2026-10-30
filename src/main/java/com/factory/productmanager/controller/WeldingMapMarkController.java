package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.AggregatedWeldingMapMarkResponse;
import com.factory.productmanager.model.dto.WeldingMapMarkRequest;
import com.factory.productmanager.model.dto.WeldingMapMarkResponse;
import com.factory.productmanager.service.WeldingMapMarkService;
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
public class WeldingMapMarkController {

    private final WeldingMapMarkService weldingMapMarkService;

    @GetMapping("/api/pieces/{pieceId}/welding-marks")
    public ResponseEntity<List<WeldingMapMarkResponse>> getMarks(@PathVariable UUID pieceId) {
        return ResponseEntity.ok(weldingMapMarkService.getMarksByPiece(pieceId));
    }

    /** نقاط جوش همه شماره‌های یک ردیف/تعریف قطعه روی نقشه مشترک (با فیلتر اختیاری نوع عیب) */
    @GetMapping("/api/piece-definitions/{definitionId}/welding-marks")
    public ResponseEntity<List<AggregatedWeldingMapMarkResponse>> getMarksByDefinition(
            @PathVariable UUID definitionId,
            @RequestParam(required = false) String defectType
    ) {
        return ResponseEntity.ok(weldingMapMarkService.getMarksByDefinition(definitionId, defectType));
    }

    @PostMapping("/api/pieces/{pieceId}/welding-marks")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_WELDING_MAP')")
    public ResponseEntity<WeldingMapMarkResponse> createMark(
            @PathVariable UUID pieceId,
            @Valid @RequestBody WeldingMapMarkRequest request,
            Authentication authentication
    ) {
        WeldingMapMarkResponse response = weldingMapMarkService.createMark(pieceId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/api/welding-marks/{id}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_WELDING_MAP')")
    public ResponseEntity<Void> deleteMark(@PathVariable UUID id) {
        weldingMapMarkService.deleteMark(id);
        return ResponseEntity.noContent().build();
    }
}
