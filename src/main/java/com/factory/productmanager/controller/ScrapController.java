package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.service.ScrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor
public class ScrapController {
    private final ScrapService scraps;
    @GetMapping("/api/pieces/{pieceId}/scrap-dispatches")
    public List<ScrapDispatchResponse> history(@PathVariable UUID pieceId) { return scraps.history(pieceId); }
    @PatchMapping("/api/pieces/{pieceId}/scrap-dispatch")
    @PreAuthorize("@permissionChecker.has(authentication, 'MOVE_PIECE_STAGE')")
    public ScrapDispatchResponse edit(@PathVariable UUID pieceId, @RequestBody ScrapRequest request, Authentication auth) {
        return scraps.edit(pieceId,request,auth.getName());
    }
    @GetMapping("/api/dashboard/scrap-report")
    @PreAuthorize("@permissionChecker.has(authentication, 'VIEW_DASHBOARD')")
    public List<ScrapDispatchResponse> report(@RequestParam(required=false) UUID projectId) { return scraps.report(projectId); }
}
