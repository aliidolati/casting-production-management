package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.RadiographyDefectRequest;
import com.factory.productmanager.model.dto.RadiographyDefectResponse;
import com.factory.productmanager.service.RadiographyDefectService;
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
public class RadiographyDefectController {

    private final RadiographyDefectService radiographyDefectService;

    // ثبت عیب رادیوگرافی کاری است که اپراتور روی خط تولید انجام می‌دهد؛
    // بر خلاف ساخت پروژه/قطعه، هم ادمین و هم اپراتور به آن دسترسی دارند (بدون @PreAuthorize)
    @PostMapping("/api/pieces/{pieceId}/radiography-defects")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_RADIOGRAPHY_DEFECTS')")
    public ResponseEntity<RadiographyDefectResponse> createDefect(
            @PathVariable UUID pieceId,
            @Valid @RequestBody RadiographyDefectRequest request,
            Authentication authentication
    ) {
        RadiographyDefectResponse response = radiographyDefectService.createDefect(pieceId, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/pieces/{pieceId}/radiography-defects")
    public ResponseEntity<List<RadiographyDefectResponse>> getDefectsByPiece(@PathVariable UUID pieceId) {
        return ResponseEntity.ok(radiographyDefectService.getDefectsByPiece(pieceId));
    }

    @PatchMapping("/api/radiography-defects/{id}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_RADIOGRAPHY_DEFECTS')")
    public ResponseEntity<RadiographyDefectResponse> updateDefect(
            @PathVariable UUID id,
            @Valid @RequestBody RadiographyDefectRequest request
    ) {
        return ResponseEntity.ok(radiographyDefectService.updateDefect(id, request));
    }

    @DeleteMapping("/api/radiography-defects/{id}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_RADIOGRAPHY_DEFECTS')")
    public ResponseEntity<Void> deleteDefect(@PathVariable UUID id, Authentication authentication) {
        radiographyDefectService.deleteDefect(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/radiography-defects/{id}/confirm-welding")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_RADIOGRAPHY_DEFECTS')")
    public ResponseEntity<RadiographyDefectResponse> confirmWelding(@PathVariable UUID id) {
        return ResponseEntity.ok(radiographyDefectService.confirmWelding(id));
    }
}
