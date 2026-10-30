package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.CreatePieceHeatTreatmentRequest;
import com.factory.productmanager.model.dto.HeatTreatmentBatchResponse;
import com.factory.productmanager.model.dto.NextBatchCodeResponse;
import com.factory.productmanager.model.dto.PieceHeatTreatmentResponse;
import com.factory.productmanager.model.dto.UpdateHeatTreatmentBatchRequest;
import com.factory.productmanager.service.HeatTreatmentService;
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
public class HeatTreatmentController {

    private final HeatTreatmentService heatTreatmentService;

    @GetMapping("/api/pieces/{pieceId}/heat-treatments")
    public ResponseEntity<List<PieceHeatTreatmentResponse>> getByPiece(
            @PathVariable UUID pieceId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(heatTreatmentService.getByPiece(pieceId, authentication.getName()));
    }

    @PostMapping("/api/pieces/{pieceId}/heat-treatments")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_HEAT_TREATMENT_LOG')")
    public ResponseEntity<PieceHeatTreatmentResponse> recordForPiece(
            @PathVariable UUID pieceId,
            @Valid @RequestBody CreatePieceHeatTreatmentRequest request,
            Authentication authentication
    ) {
        PieceHeatTreatmentResponse response =
                heatTreatmentService.recordForPiece(pieceId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/api/heat-treatments/{id}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_HEAT_TREATMENT_LOG')")
    public ResponseEntity<Void> deleteRecord(@PathVariable UUID id) {
        heatTreatmentService.deleteRecord(id);
        return ResponseEntity.noContent().build();
    }

    /** محموله‌های امروز، برای فهرست پیشنهاد در فرم ثبت */
    @GetMapping("/api/heat-treatment-batches")
    public ResponseEntity<List<HeatTreatmentBatchResponse>> getTodayBatches() {
        return ResponseEntity.ok(heatTreatmentService.getTodayBatches());
    }

    /**
     * استعلام دقیق یک کد محموله. کد به‌صورت query parameter می‌آید تا کدهای
     * حاوی کاراکترهای مسیر هم بدون مشکل جست‌وجو شوند.
     */
    @GetMapping("/api/heat-treatment-batches/by-code")
    public ResponseEntity<HeatTreatmentBatchResponse> findBatchByCode(@RequestParam String code) {
        return heatTreatmentService.findBatchByCode(code)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** کد پیشنهادی برای محموله‌ی جدید؛ code می‌تواند null باشد */
    @GetMapping("/api/heat-treatment-batches/next-code")
    public ResponseEntity<NextBatchCodeResponse> nextBatchCode() {
        return ResponseEntity.ok(
                new NextBatchCodeResponse(heatTreatmentService.suggestNextBatchCode().orElse(null))
        );
    }

    @PatchMapping("/api/heat-treatment-batches/{id}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_HEAT_TREATMENT_LOG')")
    public ResponseEntity<HeatTreatmentBatchResponse> updateBatch(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHeatTreatmentBatchRequest request
    ) {
        return ResponseEntity.ok(heatTreatmentService.updateBatch(id, request));
    }
}
