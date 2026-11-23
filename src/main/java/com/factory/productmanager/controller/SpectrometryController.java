package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.PageResponse;
import com.factory.productmanager.model.dto.SpectrometryDto.*;
import com.factory.productmanager.service.SpectrometryService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/spectrometry")
@RequiredArgsConstructor
public class SpectrometryController {
    private static final String VIEW = "@permissionChecker.has(authentication, 'VIEW_SPECTROMETRY')";
    private final SpectrometryService service;

    @GetMapping
    @PreAuthorize(VIEW)
    public PageResponse<Row> getHistory(@Valid @ModelAttribute Filters filters,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size,
                                        @RequestParam(defaultValue = "false") boolean ascending) {
        return service.history(filters, page, size, ascending);
    }

    @GetMapping("/points")
    @PreAuthorize(VIEW)
    public List<Point> getPoints(@Valid @ModelAttribute Filters filters) {
        return service.points(filters);
    }

    @GetMapping("/csv")
    @PreAuthorize(VIEW)
    public void download(@Valid @ModelAttribute Filters filters,
                         @RequestParam(defaultValue = "false") boolean ascending,
                         HttpServletResponse response) throws IOException {
        service.validateFilters(filters);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"spectrometry.csv\"");
        service.export(response.getOutputStream(), filters, ascending);
    }

    @GetMapping("/pieces")
    @PreAuthorize("@permissionChecker.hasAny(authentication, 'CREATE_SPECTROMETRY_TEST', 'EDIT_SPECTROMETRY_TEST')")
    public List<PieceOption> getPieceOptions(@RequestParam UUID definitionId) {
        return service.pieceOptions(definitionId);
    }

    @PostMapping("/batch")
    @PreAuthorize("@permissionChecker.has(authentication, 'CREATE_SPECTROMETRY_TEST')")
    public ResponseEntity<BatchResult> save(@Valid @RequestBody Batch batch, Authentication authentication) {
        BatchResult result = service.saveBatch(authentication.getName(), batch);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.has(authentication, 'EDIT_SPECTROMETRY_TEST')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable UUID id, @Valid @RequestBody Update update) {
        service.update(id, update);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.has(authentication, 'DELETE_SPECTROMETRY_TEST')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam long version) {
        service.delete(id, version);
    }
}
