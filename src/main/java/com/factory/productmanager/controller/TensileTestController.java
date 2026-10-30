package com.factory.productmanager.controller;

import com.factory.productmanager.model.entity.TensileSampleLabels;
import com.factory.productmanager.model.dto.CreateTensileTestRequest;
import com.factory.productmanager.model.dto.PendingTensileReviewItem;
import com.factory.productmanager.model.dto.SaveTensileDispositionRequest;
import com.factory.productmanager.model.dto.TensileTestResponse;
import com.factory.productmanager.service.TensileTestService;
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
public class TensileTestController {

    private final TensileTestService tensileTestService;

    @GetMapping("/api/pieces/{pieceId}/tensile-tests")
    public ResponseEntity<List<TensileTestResponse>> getTestsByPiece(@PathVariable UUID pieceId) {
        return ResponseEntity.ok(tensileTestService.getTestsByPiece(pieceId));
    }

    // ثبت تست توسط اپراتور مرحله‌ی کشش
    @PostMapping("/api/pieces/{pieceId}/tensile-tests")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECE_ASSIGNMENT')")
    public ResponseEntity<TensileTestResponse> createTest(
            @PathVariable UUID pieceId,
            @Valid @RequestBody CreateTensileTestRequest request,
            Authentication authentication
    ) {
        TensileTestResponse response = tensileTestService.createTest(pieceId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/api/tensile-tests/{tensileTestId}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECE_ASSIGNMENT')")
    public ResponseEntity<TensileTestResponse> updateTest(
            @PathVariable UUID tensileTestId,
            @Valid @RequestBody CreateTensileTestRequest request
    ) {
        return ResponseEntity.ok(tensileTestService.updateTest(tensileTestId, request));
    }

    @DeleteMapping("/api/tensile-tests/{tensileTestId}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECE_ASSIGNMENT')")
    public ResponseEntity<Void> deleteTest(@PathVariable UUID tensileTestId) {
        tensileTestService.deleteTest(tensileTestId);
        return ResponseEntity.noContent().build();
    }

    // تعیین‌تکلیف فقط در اختیار مسئول کشش است
    @PutMapping("/api/tensile-tests/{tensileTestId}/disposition")
    @PreAuthorize("@permissionChecker.has(authentication, 'REVIEW_TENSILE_TEST')")
    public ResponseEntity<TensileTestResponse> saveDisposition(
            @PathVariable UUID tensileTestId,
            @Valid @RequestBody SaveTensileDispositionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                tensileTestService.saveDisposition(tensileTestId, request, authentication.getName())
        );
    }

    @GetMapping("/api/tensile-tests/sample-labels")
    public ResponseEntity<List<String>> getSampleLabels() {
        return ResponseEntity.ok(TensileSampleLabels.ALLOWED);
    }

    @GetMapping("/api/tensile-tests/pending-review")
    public ResponseEntity<List<PendingTensileReviewItem>> getPendingReview(
            @RequestParam(required = false) UUID projectId
    ) {
        return ResponseEntity.ok(tensileTestService.getPendingReview(projectId));
    }
}
