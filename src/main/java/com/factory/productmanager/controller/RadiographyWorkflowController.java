package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.PageResponse;
import com.factory.productmanager.model.dto.RadiographyWorkflowDtos.*;
import com.factory.productmanager.model.dto.UserRefResponse;
import com.factory.productmanager.service.RadiographyWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * API گردش‌کار تفسیر رادیوگرافی. این لایه فقط دسترسی کلی (دسترسی‌های REVIEW/INTERPRET) را می‌سنجد؛
 * سطح دوم (مثلاً «فقط مفسرِ مسئولِ همین گزارش») و همه‌ی قوانین گردش‌کار در RadiographyWorkflowService اعمال می‌شود.
 */
@RestController
@RequestMapping("/api/radiography")
@RequiredArgsConstructor
public class RadiographyWorkflowController {

    private static final String REVIEW = "@permissionChecker.has(authentication, 'REVIEW_RADIOGRAPHY_REPORT')";
    private static final String INTERPRET = "@permissionChecker.has(authentication, 'INTERPRET_RADIOGRAPHY')";

    private final RadiographyWorkflowService workflowService;

    @GetMapping("/queue")
    @PreAuthorize(REVIEW)
    public PageResponse<QueueItemResponse> queue(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return workflowService.queue(search, page, size, authentication.getName());
    }

    @GetMapping("/interpreters")
    @PreAuthorize(REVIEW)
    public List<UserRefResponse> interpreters(Authentication authentication) {
        return workflowService.listInterpreters(authentication.getName());
    }

    @GetMapping("/pieces/{pieceId}/workflow")
    public PieceStateResponse pieceState(@PathVariable UUID pieceId, Authentication authentication) {
        return workflowService.getPieceState(pieceId, authentication.getName());
    }

    @PostMapping("/pieces/{pieceId}/workflow/assign")
    @PreAuthorize(REVIEW)
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse assign(
            @PathVariable UUID pieceId,
            @Valid @RequestBody AssignRequest request,
            Authentication authentication
    ) {
        return workflowService.assignInterpreter(pieceId, request.interpreterId(), authentication.getName());
    }

    @GetMapping("/workflows/{workflowId}")
    public WorkflowResponse get(@PathVariable UUID workflowId, Authentication authentication) {
        return workflowService.get(workflowId, authentication.getName());
    }

    @PostMapping("/workflows/{workflowId}/reassign")
    @PreAuthorize(REVIEW)
    public WorkflowResponse reassign(
            @PathVariable UUID workflowId,
            @Valid @RequestBody AssignRequest request,
            Authentication authentication
    ) {
        return workflowService.reassignInterpreter(workflowId, request.interpreterId(), authentication.getName());
    }

    @PostMapping("/workflows/{workflowId}/cancel")
    @PreAuthorize(REVIEW)
    public WorkflowResponse cancel(
            @PathVariable UUID workflowId,
            @RequestBody ReasonRequest request,
            Authentication authentication
    ) {
        return workflowService.cancelWorkflow(workflowId, request.reason(), authentication.getName());
    }

    @PostMapping("/workflows/{workflowId}/interpreter-approve")
    @PreAuthorize(INTERPRET)
    public WorkflowResponse interpreterApprove(
            @PathVariable UUID workflowId,
            @RequestBody ConfirmRequest request,
            Authentication authentication
    ) {
        return workflowService.issueAndApproveByInterpreter(workflowId, request.confirmed(), authentication.getName());
    }

    @PostMapping("/workflows/{workflowId}/manager-approve")
    @PreAuthorize(REVIEW)
    public WorkflowResponse managerApprove(
            @PathVariable UUID workflowId,
            @RequestBody ConfirmRequest request,
            Authentication authentication
    ) {
        return workflowService.approveByManager(workflowId, request.confirmed(), authentication.getName());
    }

    @PostMapping("/workflows/{workflowId}/manager-reject")
    @PreAuthorize(REVIEW)
    public WorkflowResponse managerReject(
            @PathVariable UUID workflowId,
            @RequestBody ReasonRequest request,
            Authentication authentication
    ) {
        return workflowService.rejectByManager(workflowId, request.reason(), authentication.getName());
    }

    @GetMapping("/workflows/{workflowId}/versions/{versionId}/document")
    public DocumentResponse document(
            @PathVariable UUID workflowId,
            @PathVariable UUID versionId,
            Authentication authentication
    ) {
        return workflowService.getDocument(workflowId, versionId, authentication.getName());
    }
}