package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.model.entity.TaskPriority;
import com.factory.productmanager.model.entity.TaskStatus;
import com.factory.productmanager.service.TaskQueryService;
import com.factory.productmanager.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** پروفایل کاربر و وظایف او. خودِ کاربر یا مدیر وظایف/ادمین؛ دیگران ۴۰۴ می‌گیرند. */
@RestController
@RequestMapping("/api/users/{userId}")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;
    private final TaskQueryService queryService;

    @GetMapping("/profile")
    public UserProfileResponse profile(@PathVariable UUID userId, Authentication authentication) {
        return profileService.getProfile(userId, authentication.getName());
    }

    @PatchMapping("/profile")
    @PreAuthorize("hasRole('ADMIN')")
    public UserProfileResponse updateProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserProfileRequest request,
            Authentication authentication
    ) {
        return profileService.updateProfile(userId, request, authentication.getName());
    }

    @GetMapping("/tasks")
    public PageResponse<TaskSummaryResponse> tasks(
            @PathVariable UUID userId,
            @RequestParam(required = false) List<TaskStatus> status,
            @RequestParam(required = false) List<TaskPriority> priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false, name = "q") String search,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TaskFilter.DEFAULT_SIZE) int size,
            Authentication authentication
    ) {
        TaskFilter filter = TaskController.buildFilter(status, priority, null, null, null, null,
                null, dueFrom, dueTo, overdue, search, false, sort, dir, page, size);
        return queryService.searchForUser(userId, filter, authentication.getName());
    }
}
