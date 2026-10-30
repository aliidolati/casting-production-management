package com.factory.productmanager.service;

import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.model.entity.Task;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.PieceRepository;
import com.factory.productmanager.repository.TaskRepository;
import com.factory.productmanager.repository.TaskSpecifications;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** خواندنِ وظایف: لیست صفحه‌بندی‌شده، جزئیات و جستجوهای کمکی فرم (مسئول و قطعه) */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TaskQueryService {

    private static final int LOOKUP_LIMIT = 20;

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final PieceRepository pieceRepository;
    private final TaskLookup lookup;
    private final TaskAccessPolicy policy;
    private final TaskMapper mapper;
    private final TaskChecklistService checklistService;
    private final TaskDetailAssembler assembler;
    private final TaskActivityService activityService;
    private final Clock clock;

    public PageResponse<TaskSummaryResponse> search(TaskFilter filter, String username) {
        User actor = lookup.actor(username);
        return page(filter, actor);
    }

    /**
     * وظایف محول‌شده به یک کاربر مشخص (برای صفحه‌ی پروفایل). خودِ کاربر و مدیران/ادمین مجازند؛
     * دیگران همان پاسخ «پیدا نشد» را می‌گیرند. حتی برای مدیر، فقط وظایفی که اجازه‌ی دیدنشان را دارد برمی‌گردد.
     */
    public PageResponse<TaskSummaryResponse> searchForUser(UUID targetUserId, TaskFilter filter, String username) {
        User actor = lookup.actor(username);
        requireCanViewProfile(actor, targetUserId);
        return page(filter.withAssignee(targetUserId), actor);
    }

    public TaskDetailResponse detail(UUID taskId, String username) {
        User actor = lookup.actor(username);
        Task task = lookup.visibleTask(taskId, actor);
        return assembler.assemble(task, actor);
    }

    public PageResponse<TaskActivityResponse> activities(UUID taskId, int page, int size, String username) {
        User actor = lookup.actor(username);
        Task task = lookup.visibleTask(taskId, actor);
        return activityService.list(task, page, size);
    }

    /** کاربرانی که می‌شود وظیفه به آن‌ها داد (فقط فیلدهای ضروری، بدون نقش/دسترسی) */
    public List<UserRefResponse> assignableUsers() {
        return userRepository.findAssignableUsers(Permission.RECEIVE_TASKS);
    }

    /** جستجوی سبک قطعات برای فرم وظیفه؛ حداکثر ۲۰ نتیجه */
    public List<PartRefResponse> lookupParts(UUID projectId, String keyword) {
        String term = keyword == null ? "" : keyword.trim();
        if (term.length() > 100) {
            term = term.substring(0, 100);
        }
        PageRequest limit = PageRequest.of(0, LOOKUP_LIMIT);
        return (projectId == null
                ? pieceRepository.lookupForTasks(term, limit)
                : pieceRepository.lookupForTasksInProject(projectId, term, limit))
                .stream().map(mapper::toPartRef).toList();
    }

    public void requireCanViewProfile(User viewer, UUID targetUserId) {
        if (!canViewProfile(viewer, targetUserId)) {
            throw new ResourceNotFoundException("کاربر پیدا نشد");
        }
    }

    public boolean canViewProfile(User viewer, UUID targetUserId) {
        return viewer.getId().equals(targetUserId) || policy.hasManagePermission(viewer);
    }

    // ------------------------------------------------------------------

    private PageResponse<TaskSummaryResponse> page(TaskFilter filter, User actor) {
        LocalDateTime now = LocalDateTime.now(clock);
        int size = Math.min(Math.max(filter.size(), 1), TaskFilter.MAX_SIZE);
        int pageIndex = Math.max(filter.page(), 0);
        TaskSort sort = filter.sort() == null ? TaskSort.SMART : filter.sort();

        Specification<Task> spec = Specification.allOf(
                TaskSpecifications.visibleTo(actor.getId(), policy.isAdmin(actor), policy.hasManagePermission(actor), policy.canReviewMelt(actor)),
                filter.mine() ? TaskSpecifications.inbox(actor.getId(), policy.canReviewMelt(actor)) : Specification.unrestricted(),
                TaskSpecifications.matching(filter.mine() ? filter.withAssignee(null) : filter, now),
                TaskSpecifications.orderedBy(sort, filter.ascending(), now)
        );

        Page<Task> result = taskRepository.findAll(spec, PageRequest.of(pageIndex, size));
        Map<UUID, ChecklistProgressResponse> progress = checklistService.progressByTask(
                result.getContent().stream().map(Task::getId).toList());
        ChecklistProgressResponse empty = ChecklistProgressResponse.of(0, 0);

        return PageResponse.of(result, result.getContent().stream()
                .map(task -> mapper.toSummary(task, progress.getOrDefault(task.getId(), empty)))
                .toList());
    }
}
