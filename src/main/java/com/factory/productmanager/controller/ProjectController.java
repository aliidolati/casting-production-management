package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.BulkArchiveResultResponse;
import com.factory.productmanager.model.dto.ProjectRequest;
import com.factory.productmanager.model.dto.ProjectResponse;
import com.factory.productmanager.service.PieceService;
import com.factory.productmanager.service.ProjectService;
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
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final PieceService pieceService;

    // ساخت پروژه‌ی جدید یک عملیات ساختاری است و فقط باید در اختیار مدیر باشد
    @PostMapping
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PROJECTS')")
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody ProjectRequest request) {
        ProjectResponse response = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getAllProjects() {
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProjectById(@PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getProjectById(id));
    }

    // ویرایش پروژه (کد/نام) یک عملیات ساختاری است و فقط باید در اختیار مدیر باشد
    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PROJECTS')")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectRequest request
    ) {
        return ResponseEntity.ok(projectService.updateProject(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PROJECTS')")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/archive-pieces")
    @PreAuthorize("@permissionChecker.has(authentication, 'MANAGE_PIECES')")
    public ResponseEntity<BulkArchiveResultResponse> archivePieces(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pieceService.archivePiecesByProject(id, authentication.getName()));
    }
}
