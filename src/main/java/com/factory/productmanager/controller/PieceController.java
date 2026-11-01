package com.factory.productmanager.controller;


import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.model.entity.HeatTreatmentSubStage;
import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.RadiographySubStage;
import com.factory.productmanager.model.entity.RoughMachiningSubStage;
import com.factory.productmanager.service.PieceService;
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
@RequestMapping("/api/pieces")
@RequiredArgsConstructor
public class PieceController {

    private final PieceService pieceService;

    // ساخت قطعه‌ی جدید یک عملیات ساختاری است و فقط باید در اختیار مدیر باشد
    @PostMapping
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECES')")
    public ResponseEntity<PieceResponse> createPiece(@Valid @RequestBody PieceRequest request) {
        PieceResponse response = pieceService.createPiece(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<PieceResponse>> getPiecesByDefinition(
            @RequestParam UUID pieceDefinitionId,
            @RequestParam(defaultValue = "false") boolean archived
    ) {
        return ResponseEntity.ok(pieceService.getPiecesByDefinition(pieceDefinitionId, archived));
    }

    // ساخت دسته‌ای قطعات (مثلاً ۱۰۰ قطعه با یک درخواست) یک عملیات ساختاری است و فقط باید در اختیار مدیر باشد
    @PostMapping("/bulk")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECES')")
    public ResponseEntity<List<PieceResponse>> bulkCreatePieces(@Valid @RequestBody BulkCreatePieceRequest request) {
        List<PieceResponse> response = pieceService.bulkCreatePieces(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/by-ref")
    public ResponseEntity<PieceResponse> getPieceByRef(@RequestParam String code) {
        return ResponseEntity.ok(pieceService.findByRef(code));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PieceResponse> getPieceById(@PathVariable UUID id) {
        return ResponseEntity.ok(pieceService.getPieceById(id));
    }

    // ویرایش شماره‌ی قطعه یک عملیات ساختاری است و فقط باید در اختیار مدیر باشد
    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECES')")
    public ResponseEntity<PieceResponse> updatePiece(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePieceRequest request
    ) {
        return ResponseEntity.ok(pieceService.updatePiece(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECES')")
    public ResponseEntity<Void> deletePiece(@PathVariable UUID id, Authentication authentication) {
        pieceService.deletePiece(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECES')")
    public ResponseEntity<PieceResponse> restorePiece(@PathVariable UUID id) {
        return ResponseEntity.ok(pieceService.restorePiece(id));
    }

    @PostMapping("/bulk/archive")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECES')")
    public ResponseEntity<BulkArchiveResultResponse> bulkArchivePieces(
            @Valid @RequestBody BulkPieceIdsRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pieceService.bulkArchivePieces(request.getPieceIds(), authentication.getName()));
    }

    @PostMapping("/bulk/restore")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECES')")
    public ResponseEntity<BulkArchiveResultResponse> bulkRestorePieces(
            @Valid @RequestBody BulkPieceIdsRequest request
    ) {
        return ResponseEntity.ok(pieceService.bulkRestorePieces(request.getPieceIds()));
    }

    @PatchMapping("/{id}/process-date")
    @PreAuthorize("@permissionChecker.has(authentication, 'MOVE_PIECE_STAGE')")
    public ResponseEntity<PieceResponse> updateProcessDate(
            @PathVariable UUID id,
            @RequestBody UpdateProcessDateRequest request
    ) {
        return ResponseEntity.ok(pieceService.updateProcessDate(id, request));
    }

    // اپراتور و ادمین هر دو مجاز به تغییر وضعیت قطعه هستند
    @PatchMapping("/{id}/stage")
    @PreAuthorize("@permissionChecker.has(authentication, 'MOVE_PIECE_STAGE')")
    public ResponseEntity<PieceResponse> updateStage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStageRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pieceService.updateStage(id, request, authentication.getName()));
    }

    @PostMapping("/{id}/advance")
    @PreAuthorize("@permissionChecker.hasAny(authentication, 'MOVE_PIECE_STAGE', 'UPDATE_RADIOGRAPHY_SUBSTAGE', 'UPDATE_HEAT_TREATMENT_SUBSTAGE', 'UPDATE_ROUGH_MACHINING_SUBSTAGE')")
    public ResponseEntity<PieceResponse> advancePiece(
            @PathVariable UUID id,
            @RequestBody(required = false) AdvancePieceRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pieceService.advancePiece(
                id,
                request == null ? new AdvancePieceRequest() : request,
                authentication.getName()
        ));
    }

    // پیشرفت بین زیرمرحله‌های رادیوگرافی، مثل تغییر وضعیت اصلی، برای اپراتور و ادمین هر دو باز است
    @PatchMapping("/{id}/radiography-substage")
    @PreAuthorize("@permissionChecker.has(authentication, 'UPDATE_RADIOGRAPHY_SUBSTAGE')")
    public ResponseEntity<PieceResponse> updateRadiographySubStage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRadiographySubStageRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pieceService.updateRadiographySubStage(id, request, authentication.getName()));
    }

    // پیشرفت بین زیرمرحله‌های عملیات حرارتی، مثل تغییر وضعیت اصلی، برای اپراتور و ادمین هر دو باز است
    @PatchMapping("/{id}/heat-treatment-substage")
    @PreAuthorize("@permissionChecker.has(authentication, 'UPDATE_HEAT_TREATMENT_SUBSTAGE')")
    public ResponseEntity<PieceResponse> updateHeatTreatmentSubStage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHeatTreatmentSubStageRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pieceService.updateHeatTreatmentSubStage(id, request, authentication.getName()));
    }

    // پیشرفت بین زیرمرحله‌های خشن‌تراشی؛ عقب‌گرد در سرویس فقط برای ادمین باز است
    @PatchMapping("/{id}/rough-machining-substage")
    @PreAuthorize("@permissionChecker.has(authentication, 'UPDATE_ROUGH_MACHINING_SUBSTAGE')")
    public ResponseEntity<PieceResponse> updateRoughMachiningSubStage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoughMachiningSubStageRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pieceService.updateRoughMachiningSubStage(id, request, authentication.getName()));
    }

    // تغییر وضعیت دسته‌جمعی چند قطعه هم‌زمان؛ مثل تغییر تکی، هم اپراتور هم ادمین مجازند
    @PatchMapping("/bulk/stage")
    @PreAuthorize("@permissionChecker.has(authentication, 'MOVE_PIECE_STAGE')")
    public ResponseEntity<List<PieceResponse>> bulkUpdateStage(
            @Valid @RequestBody BulkUpdateStageRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pieceService.bulkUpdateStage(request, authentication.getName()));
    }

    // تغییر دسته‌جمعی زیرمرحله‌ی رادیوگرافی؛ مثل تغییر تکی، هم اپراتور هم ادمین مجازند
    @PatchMapping("/bulk/radiography-substage")
    @PreAuthorize("@permissionChecker.has(authentication, 'UPDATE_RADIOGRAPHY_SUBSTAGE')")
    public ResponseEntity<List<PieceResponse>> bulkUpdateRadiographySubStage(
            @Valid @RequestBody BulkUpdateRadiographySubStageRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pieceService.bulkUpdateRadiographySubStage(request, authentication.getName()));
    }

    // تغییر دسته‌جمعی زیرمرحله‌ی عملیات حرارتی؛ مثل تغییر تکی، هم اپراتور هم ادمین مجازند
    @PatchMapping("/bulk/heat-treatment-substage")
    @PreAuthorize("@permissionChecker.has(authentication, 'UPDATE_HEAT_TREATMENT_SUBSTAGE')")
    public ResponseEntity<List<PieceResponse>> bulkUpdateHeatTreatmentSubStage(
            @Valid @RequestBody BulkUpdateHeatTreatmentSubStageRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pieceService.bulkUpdateHeatTreatmentSubStage(request, authentication.getName()));
    }

    // تغییر دسته‌جمعی زیرمرحله‌ی خشن‌تراشی؛ همه‌یا-هیچ، شامل بررسی عقب‌گرد برای کل دسته
    @PatchMapping("/bulk/rough-machining-substage")
    @PreAuthorize("@permissionChecker.has(authentication, 'UPDATE_ROUGH_MACHINING_SUBSTAGE')")
    public ResponseEntity<List<PieceResponse>> bulkUpdateRoughMachiningSubStage(
            @Valid @RequestBody BulkUpdateRoughMachiningSubStageRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pieceService.bulkUpdateRoughMachiningSubStage(request, authentication.getName()));
    }

    @GetMapping("/{id}/stage-process-dates")
    public ResponseEntity<List<StageProcessDateResponse>> getStageProcessDates(@PathVariable UUID id) {
        return ResponseEntity.ok(pieceService.getStageProcessDates(id));
    }

    @GetMapping("/{id}/sub-stage-process-dates")
    public ResponseEntity<List<SubStageProcessDateResponse>> getSubStageProcessDates(@PathVariable UUID id) {
        return ResponseEntity.ok(pieceService.getSubStageProcessDates(id));
    }

    @PatchMapping("/{id}/sub-stage-process-dates")
    @PreAuthorize("@permissionChecker.has(authentication, 'MOVE_PIECE_STAGE') or @permissionChecker.has(authentication, 'MANAGE_PIECES')")
    public ResponseEntity<Void> updateSubStageProcessDate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubStageProcessDateRequest request
    ) {
        pieceService.updateSubStageProcessDate(id, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/stage-process-dates")
    @PreAuthorize("@permissionChecker.has(authentication, 'MOVE_PIECE_STAGE') or @permissionChecker.has(authentication, 'MANAGE_PIECES')")
    public ResponseEntity<Void> updateStageProcessDate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStageProcessDateRequest request
    ) {
        pieceService.updateStageProcessDate(id, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stage-history")
    public ResponseEntity<List<PieceStageHistoryResponse>> getStageHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(pieceService.getStageHistory(id));
    }

    @GetMapping("/{id}/substage-history")
    public ResponseEntity<List<PieceSubStageHistoryResponse>> getSubStageHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(pieceService.getSubStageHistory(id));
    }

    @GetMapping("/{id}/welding-dispatches")
    public ResponseEntity<List<PieceWeldingDispatchResponse>> getWeldingDispatches(@PathVariable UUID id) {
        return ResponseEntity.ok(pieceService.getWeldingDispatches(id));
    }

    @PostMapping("/{id}/welding-station")
    @PreAuthorize("@permissionChecker.has(authentication, 'MOVE_PIECE_STAGE')")
    public ResponseEntity<PieceResponse> sendToWeldingStation(
            @PathVariable UUID id,
            @Valid @RequestBody SendToWeldingRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pieceService.sendToWeldingStation(id, request, authentication.getName()));
    }

    @PatchMapping("/{id}/welding-dispatches/{dispatchId}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MOVE_PIECE_STAGE')")
    public ResponseEntity<PieceWeldingDispatchResponse> updateWeldingDispatch(
            @PathVariable UUID id, @PathVariable UUID dispatchId,
            @Valid @RequestBody UpdateWeldingDispatchRequest request
    ) {
        return ResponseEntity.ok(pieceService.updateWeldingDispatch(id, dispatchId, request));
    }

    @PostMapping("/{id}/welding-station/complete")
    @PreAuthorize("@permissionChecker.has(authentication, 'MOVE_PIECE_STAGE')")
    public ResponseEntity<PieceResponse> completeWeldingStation(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteWeldingRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pieceService.completeWeldingStation(id, request, authentication.getName()));
    }

    @DeleteMapping("/{id}/stage-history/{historyId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteStageHistory(
            @PathVariable UUID id,
            @PathVariable UUID historyId,
            Authentication authentication
    ) {
        pieceService.deleteStageHistory(id, historyId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/substage-history/{historyId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteSubStageHistory(
            @PathVariable UUID id,
            @PathVariable UUID historyId,
            Authentication authentication
    ) {
        pieceService.deleteSubStageHistory(id, historyId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/all")
    public ResponseEntity<List<PieceResponse>> getAllPieces() {
        return ResponseEntity.ok(pieceService.getAllPieces());
    }

    @GetMapping("/search")
    public ResponseEntity<List<PieceResponse>> searchPieces(
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(pieceService.searchPieces(q));
    }

    @GetMapping("/tracking")
    public ResponseEntity<PageResponse<PieceResponse>> listTrackingPieces(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PieceStage stage,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateFrom,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateTo,
            @RequestParam(required = false) RadiographySubStage radiographySubStage,
            @RequestParam(required = false) HeatTreatmentSubStage heatTreatmentSubStage,
            @RequestParam(required = false) RoughMachiningSubStage roughMachiningSubStage,
            @RequestParam(required = false) String pieceName,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(pieceService.listTrackingPieces(
                q,
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
                page,
                size
        ));
    }

    @GetMapping("/tracking/summary")
    public ResponseEntity<TrackingSummaryResponse> getTrackingSummary(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String pieceName,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateFrom,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateTo
    ) {
        return ResponseEntity.ok(pieceService.getTrackingSummary(q, pieceName, projectCode, dateFrom, dateTo));
    }
    @PatchMapping("/{id}/description")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECES')")
    public ResponseEntity<PieceResponse> updateDescription(
            @PathVariable UUID id,
            @RequestBody UpdateDescriptionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pieceService.updateDescription(id, request, authentication.getName()));
    }
}
