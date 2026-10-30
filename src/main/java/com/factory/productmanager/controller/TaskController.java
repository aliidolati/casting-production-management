package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.model.entity.TaskPriority;
import com.factory.productmanager.model.entity.TaskStatus;
import com.factory.productmanager.service.TaskQueryService;
import com.factory.productmanager.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * API وظایف. مجوز دقیق هر عملیات (از جمله «این وظیفه را می‌بینم/تغییر می‌دهم؟») در سرویس‌ها بررسی می‌شود؛
 * اینجا فقط لایه‌ی اول، یعنی داشتن دسترسی کلی {@code MANAGE_TASKS} برای عملیات مدیریتی، اعمال می‌شود.
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private static final String MANAGE = "@permissionChecker.has(authentication, 'MANAGE_TASKS')";
    private static final int MAX_SEARCH_LENGTH = 100;

    private final TaskService taskService;
    private final TaskQueryService queryService;

    @GetMapping
    public PageResponse<TaskSummaryResponse> list(
            @RequestParam(required = false) List<TaskStatus> status,
            @RequestParam(required = false) List<TaskPriority> priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID creatorId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID partId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false, name = "q") String search,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TaskFilter.DEFAULT_SIZE) int size,
            Authentication authentication
    ) {
        TaskFilter filter = buildFilter(status, priority, assigneeId, creatorId, projectId, partId,
                dueDate, dueFrom, dueTo, overdue, search, mine, sort, dir, page, size);
        return queryService.search(filter, authentication.getName());
    }

    @GetMapping("/{id}")
    public TaskDetailResponse get(@PathVariable UUID id, Authentication authentication) {
        return queryService.detail(id, authentication.getName());
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<TaskDetailResponse> create(
            @Valid @RequestBody CreateTaskRequest request,
            Authentication authentication
    ) {
        TaskDetailResponse created = taskService.create(request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/tasks/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    public TaskDetailResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequest request,
            Authentication authentication
    ) {
        return taskService.update(id, request, authentication.getName());
    }

    /** تغییر وضعیت (شروع، انتظار، تکمیل، لغو، بازگشایی). قواعد دقیق در TaskAccessPolicy است. */
    @PatchMapping("/{id}/status")
    public TaskDetailResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeStatusRequest request,
            Authentication authentication
    ) {
        return taskService.changeStatus(id, request, authentication.getName());
    }

    @PatchMapping("/{id}/priority")
    @PreAuthorize(MANAGE)
    public TaskDetailResponse changePriority(
            @PathVariable UUID id,
            @Valid @RequestBody ChangePriorityRequest request,
            Authentication authentication
    ) {
        return taskService.changePriority(id, request, authentication.getName());
    }

    @PatchMapping("/{id}/deadline")
    @PreAuthorize(MANAGE)
    public TaskDetailResponse changeDeadline(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeDeadlineRequest request,
            Authentication authentication
    ) {
        return taskService.changeDeadline(id, request, authentication.getName());
    }

    @PatchMapping("/{id}/assignee")
    @PreAuthorize(MANAGE)
    public TaskDetailResponse changeAssignee(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeAssigneeRequest request,
            Authentication authentication
    ) {
        return taskService.changeAssignee(id, request, authentication.getName());
    }

    /** «حذف» = لغو؛ وظیفه و تاریخچه‌اش حفظ می‌شود */
    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGE)
    public TaskDetailResponse cancel(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            Authentication authentication
    ) {
        return taskService.cancel(id, reason, authentication.getName());
    }

    @GetMapping("/assignees")
    @PreAuthorize(MANAGE)
    public List<UserRefResponse> assignees() {
        return queryService.assignableUsers();
    }

    @GetMapping("/lookup/parts")
    @PreAuthorize(MANAGE)
    public List<PartRefResponse> lookupParts(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false, name = "q") String keyword
    ) {
        return queryService.lookupParts(projectId, keyword);
    }

    // ---------------------------------------------------------------------

    static TaskFilter buildFilter(
            List<TaskStatus> status, List<TaskPriority> priority,
            UUID assigneeId, UUID creatorId, UUID projectId, UUID partId,
            LocalDate dueDate, LocalDate dueFrom, LocalDate dueTo, Boolean overdue,
            String search, boolean mine, String sort, String dir, int page, int size
    ) {
        TaskSort sortKey = TaskSort.fromParam(sort);
        boolean ascending = dir == null || dir.isBlank()
                ? sortKey == TaskSort.DUE_DATE
                : "asc".equalsIgnoreCase(dir);

        String term = search == null ? null : search.trim();
        if (term != null && term.length() > MAX_SEARCH_LENGTH) {
            term = term.substring(0, MAX_SEARCH_LENGTH);
        }
        Set<TaskStatus> statuses = status == null || status.isEmpty() ? null : EnumSet.copyOf(status);
        Set<TaskPriority> priorities = priority == null || priority.isEmpty() ? null : EnumSet.copyOf(priority);

        return new TaskFilter(statuses, priorities, assigneeId, creatorId, projectId, partId,
                dueDate, dueFrom, dueTo, overdue, term, mine, sortKey, ascending, page, size);
    }
}
