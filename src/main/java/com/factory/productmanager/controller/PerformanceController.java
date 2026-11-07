package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.PageResponse;
import com.factory.productmanager.model.dto.PerformanceDtos.*;
import com.factory.productmanager.service.PerformanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/users/{userId}/performance")
public class PerformanceController {
    private final PerformanceService service;
    @GetMapping
    public Employee employee(@PathVariable UUID userId, Authentication auth) {
        return service.employee(userId, auth.getName());
    }

    @GetMapping("/periods")
    public PageResponse<PeriodSummary> list(@PathVariable UUID userId, @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "12") int size, Authentication auth) {
        return service.list(userId, auth.getName(), page, size);
    }
    @GetMapping("/periods/{periodId}")
    public Period detail(@PathVariable UUID userId, @PathVariable UUID periodId, Authentication auth) {
        return service.detail(userId, periodId, auth.getName());
    }
    @GetMapping("/periods/{periodId}/events")
    public PageResponse<Event> events(@PathVariable UUID userId, @PathVariable UUID periodId,
                                     @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, Authentication auth) {
        return service.history(userId, periodId, auth.getName(), page, size);
    }
    @PostMapping("/periods")
    public Period create(@PathVariable UUID userId, @Valid @RequestBody CreatePeriod request, Authentication auth) {
        return service.create(userId, request, auth.getName());
    }
    @PostMapping("/periods/{periodId}/next")
    public Period next(@PathVariable UUID userId, @PathVariable UUID periodId, @Valid @RequestBody NextPeriod request, Authentication auth) {
        return service.next(userId, periodId, request, auth.getName());
    }
    @PostMapping("/periods/{periodId}/criteria")
    public Period add(@PathVariable UUID userId, @PathVariable UUID periodId, @Valid @RequestBody CriterionWrite request, Authentication auth) {
        return service.add(userId, periodId, request, auth.getName());
    }
    @PatchMapping("/periods/{periodId}/criteria/{criterionId}")
    public Period update(@PathVariable UUID userId, @PathVariable UUID periodId, @PathVariable UUID criterionId,
                         @Valid @RequestBody CriterionWrite request, Authentication auth) {
        return service.update(userId, periodId, criterionId, request, auth.getName());
    }
    @PatchMapping("/periods/{periodId}/criteria/{criterionId}/score")
    public Period updateScore(@PathVariable UUID userId, @PathVariable UUID periodId, @PathVariable UUID criterionId,
                              @Valid @RequestBody ScoreWrite request, Authentication auth) {
        return service.updateScore(userId, periodId, criterionId, request, auth.getName());
    }
    @DeleteMapping("/periods/{periodId}/criteria/{criterionId}")
    public Period delete(@PathVariable UUID userId, @PathVariable UUID periodId, @PathVariable UUID criterionId,
                         @Valid @RequestBody CriterionDelete request, Authentication auth) {
        return service.delete(userId, periodId, criterionId, request, auth.getName());
    }
}
