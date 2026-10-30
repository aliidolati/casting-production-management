package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.service.TaskChecklistService;
import com.factory.productmanager.service.TaskCommentService;
import com.factory.productmanager.service.TaskQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * نظرها، چک‌لیست و تاریخچه‌ی هر وظیفه. مجوز هر عملیات (دیدن وظیفه، نویسنده‌بودن، مدیر‌بودن)
 * داخل سرویس‌ها اعمال می‌شود و وظیفه‌ی غیرقابل‌دیدن همیشه ۴۰۴ می‌دهد.
 */
@RestController
@RequestMapping("/api/tasks/{taskId}")
@RequiredArgsConstructor
public class TaskCollaborationController {

    private final TaskCommentService commentService;
    private final TaskChecklistService checklistService;
    private final TaskQueryService queryService;

    // ---------------------------------------------------------------- comments

    @GetMapping("/comments")
    public List<TaskCommentResponse> comments(@PathVariable UUID taskId, Authentication authentication) {
        return commentService.list(taskId, authentication.getName());
    }

    @PostMapping("/comments")
    public ResponseEntity<TaskCommentResponse> addComment(
            @PathVariable UUID taskId,
            @Valid @RequestBody TaskCommentRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.add(taskId, request, authentication.getName()));
    }

    @PutMapping("/comments/{commentId}")
    public TaskCommentResponse editComment(
            @PathVariable UUID taskId,
            @PathVariable UUID commentId,
            @Valid @RequestBody TaskCommentRequest request,
            Authentication authentication
    ) {
        return commentService.edit(taskId, commentId, request, authentication.getName());
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID taskId,
            @PathVariable UUID commentId,
            Authentication authentication
    ) {
        commentService.delete(taskId, commentId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- checklist

    @GetMapping("/checklist")
    public ChecklistResponse checklist(@PathVariable UUID taskId, Authentication authentication) {
        return checklistService.list(taskId, authentication.getName());
    }

    @PostMapping("/checklist")
    public ResponseEntity<ChecklistResponse> addChecklistItem(
            @PathVariable UUID taskId,
            @Valid @RequestBody CreateChecklistItemRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(checklistService.add(taskId, request, authentication.getName()));
    }

    @PatchMapping("/checklist/{itemId}")
    public ChecklistResponse updateChecklistItem(
            @PathVariable UUID taskId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateChecklistItemRequest request,
            Authentication authentication
    ) {
        return checklistService.update(taskId, itemId, request, authentication.getName());
    }

    @DeleteMapping("/checklist/{itemId}")
    public ChecklistResponse deleteChecklistItem(
            @PathVariable UUID taskId,
            @PathVariable UUID itemId,
            Authentication authentication
    ) {
        return checklistService.remove(taskId, itemId, authentication.getName());
    }

    // ---------------------------------------------------------------- activity

    @GetMapping("/activities")
    public PageResponse<TaskActivityResponse> activities(
            @PathVariable UUID taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication
    ) {
        return queryService.activities(taskId, page, size, authentication.getName());
    }
}
