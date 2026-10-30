package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.service.MeltPassportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.Clock;
import java.util.*;

@RestController
@RequestMapping("/api/melt-passports")
@RequiredArgsConstructor
public class MeltPassportController {
    private final MeltPassportService service;
    private final Clock clock;

    @PostMapping("/{id}/assignment")
    public MeltPassportResponse assign(@PathVariable UUID id, @Valid @RequestBody MeltAssignmentRequest r, Authentication a) {
        return service.assign(id, r, a.getName());
    }

    @PostMapping("/piece/{pieceId}/assignment")
    public MeltPassportResponse assignPiece(@PathVariable UUID pieceId, @Valid @RequestBody MeltAssignmentRequest r, Authentication a) {
        return service.assignPiece(pieceId, r, a.getName());
    }

    @GetMapping("/today")
    public Map<String,LocalDate> today() { return Map.of("date",LocalDate.now(clock)); }

    @GetMapping
    public PageResponse<MeltPassportResponse> list(@ModelAttribute MeltPassportFilter filter,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="30") int size, Authentication a) {
        return service.search(filter,page,size,a.getName());
    }
    @GetMapping("/consumption")
    public List<MeltMaterialTotal> consumption(@ModelAttribute MeltPassportFilter filter, Authentication a) {
        return service.consumption(filter,a.getName());
    }
    @GetMapping("/candidates")
    public List<MeltPassportResponse.PieceLink> candidates(@RequestParam(defaultValue="") String q, Authentication a) {
        return service.candidates(q,a.getName());
    }
    @GetMapping("/ingot-types")
    public List<String> ingotTypes(Authentication a) { return service.ingotTypes(a.getName()); }
    @GetMapping("/materials")
    public List<MeltMaterialResponse> materials(Authentication a) { return service.materialList(a.getName()); }
    @PostMapping("/materials")
    public MeltMaterialResponse createMaterial(@Valid @RequestBody MeltMaterialRequest r, Authentication a) {
        return service.createMaterial(r,a.getName());
    }
    @PatchMapping("/materials/{id}/active")
    public MeltMaterialResponse active(@PathVariable UUID id, @RequestParam boolean active, Authentication a) {
        return service.setMaterialActive(id,active,a.getName());
    }
    @GetMapping("/piece/{pieceId}")
    public List<MeltPassportResponse> forPiece(@PathVariable UUID pieceId, Authentication a) {
        return service.forPiece(pieceId,a.getName());
    }
    @GetMapping("/{id}")
    public MeltPassportResponse get(@PathVariable UUID id, Authentication a) { return service.get(id,a.getName()); }
    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public MeltPassportResponse create(@Valid @RequestBody MeltPassportRequest r, Authentication a) {
        return service.create(r,a.getName());
    }
    @PutMapping("/{id}")
    public MeltPassportResponse update(@PathVariable UUID id, @Valid @RequestBody MeltPassportRequest r, Authentication a) {
        return service.update(id,r,a.getName());
    }
    @PostMapping("/{id}/calculate-magnesium")
    public MeltPassportResponse calculate(@PathVariable UUID id, @Valid @RequestBody MeltDecisionRequest r, Authentication a) {
        return service.calculate(id,r,a.getName());
    }
    @PostMapping("/{id}/corrections")
    public MeltPassportResponse correct(@PathVariable UUID id, @Valid @RequestBody MeltPassportCorrectionRequest r, Authentication a) {
        return service.correct(id,r,a.getName());
    }
    @PostMapping("/{id}/submit")
    public MeltPassportResponse submit(@PathVariable UUID id, @Valid @RequestBody MeltDecisionRequest r, Authentication a) {
        return service.submit(id,r,a.getName());
    }
    @PostMapping("/{id}/approve")
    public MeltPassportResponse approve(@PathVariable UUID id, @Valid @RequestBody MeltDecisionRequest r, Authentication a) {
        return service.approve(id,r,a.getName());
    }
    @PostMapping("/{id}/return")
    public MeltPassportResponse returnForCorrection(@PathVariable UUID id, @Valid @RequestBody MeltDecisionRequest r, Authentication a) {
        return service.returnForCorrection(id,r,a.getName());
    }
}
