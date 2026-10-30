package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.RadiographyReportResponse;
import com.factory.productmanager.model.dto.RadiographyTemplateRequest;
import com.factory.productmanager.model.dto.RadiographyTemplateResponse;
import com.factory.productmanager.model.dto.SaveRadiographyReportRequest;
import com.factory.productmanager.service.RadiographyReportService;
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
public class RadiographyReportController {

    private final RadiographyReportService radiographyReportService;

    /* ===========================
       قالب‌ها؛ هم‌سطح تعریف قطعه‌اند و همان دسترسی را می‌خواهند
       =========================== */

    @GetMapping("/api/piece-definitions/{pieceDefinitionId}/radiography-templates")
    public ResponseEntity<List<RadiographyTemplateResponse>> getTemplates(@PathVariable UUID pieceDefinitionId) {
        return ResponseEntity.ok(radiographyReportService.getTemplates(pieceDefinitionId));
    }

    @PostMapping("/api/piece-definitions/{pieceDefinitionId}/radiography-templates")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECE_DEFINITIONS')")
    public ResponseEntity<RadiographyTemplateResponse> createTemplate(
            @PathVariable UUID pieceDefinitionId,
            @Valid @RequestBody RadiographyTemplateRequest request,
            Authentication authentication
    ) {
        RadiographyTemplateResponse response =
                radiographyReportService.createTemplate(pieceDefinitionId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/api/radiography-templates/{templateId}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECE_DEFINITIONS')")
    public ResponseEntity<RadiographyTemplateResponse> updateTemplate(
            @PathVariable UUID templateId,
            @Valid @RequestBody RadiographyTemplateRequest request
    ) {
        return ResponseEntity.ok(radiographyReportService.updateTemplate(templateId, request));
    }

    @DeleteMapping("/api/radiography-templates/{templateId}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECE_DEFINITIONS')")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID templateId) {
        radiographyReportService.deleteTemplate(templateId);
        return ResponseEntity.noContent().build();
    }

    /* ===========================
       گزارش‌ها؛ کار اپراتور رادیوگرافی است
       =========================== */

    @GetMapping("/api/pieces/{pieceId}/radiography-reports")
    public ResponseEntity<List<RadiographyReportResponse>> getReports(@PathVariable UUID pieceId) {
        return ResponseEntity.ok(radiographyReportService.getReportsByPiece(pieceId));
    }

    /** ساخت یا ویرایش گزارش این قطعه برای قالب مشخص‌شده در بدنه‌ی درخواست */
    @PutMapping("/api/pieces/{pieceId}/radiography-reports")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_RADIOGRAPHY_DEFECTS')")
    public ResponseEntity<RadiographyReportResponse> saveReport(
            @PathVariable UUID pieceId,
            @Valid @RequestBody SaveRadiographyReportRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                radiographyReportService.saveReport(pieceId, request, authentication.getName()));
    }

    @DeleteMapping("/api/radiography-reports/{reportId}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_RADIOGRAPHY_DEFECTS')")
    public ResponseEntity<Void> deleteReport(@PathVariable UUID reportId) {
        radiographyReportService.deleteReport(reportId);
        return ResponseEntity.noContent().build();
    }
}
