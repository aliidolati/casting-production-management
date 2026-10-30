package com.factory.productmanager.service;

import com.factory.productmanager.exception.ForbiddenOperationException;
import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.model.entity.Task;
import com.factory.productmanager.model.entity.TaskActivityAction;
import com.factory.productmanager.model.entity.TaskChecklistItem;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.TaskChecklistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * چک‌لیست اختیاری داخل وظیفه.
 * <ul>
 *   <li>افزودن/حذف/تغییر عنوان آیتم: فقط مدیر وظیفه (ادمین یا سازنده‌ی مدیر).</li>
 *   <li>علامت‌زدن انجام‌شده/نشده: مدیر وظیفه یا مسئول وظیفه.</li>
 *   <li>روی وظیفه‌ی بسته‌شده (تکمیل/لغو) هیچ تغییری مجاز نیست.</li>
 * </ul>
 * هر آیتم باید واقعاً متعلق به وظیفه‌ی مسیر باشد (findByIdAndTaskId)، نه فقط «وجود داشته باشد».
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TaskChecklistService {

    public static final int MAX_ITEMS_PER_TASK = 50;
    private static final String CHECKLIST_ITEM_NOT_FOUND = "آیتم چک‌لیست پیدا نشد";

    private final TaskChecklistItemRepository checklistRepository;
    private final TaskLookup lookup;
    private final TaskAccessPolicy policy;
    private final TaskActivityService activityService;
    private final TaskMapper mapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ChecklistResponse list(UUID taskId, String username) {
        User actor = lookup.actor(username);
        Task task = lookup.visibleTask(taskId, actor);
        List<ChecklistItemResponse> items = checklistRepository
                .findByTaskIdOrderBySortOrderAscCreatedAtAsc(task.getId())
                .stream().map(mapper::toChecklistItem).toList();
        long completed = items.stream().filter(ChecklistItemResponse::completed).count();
        return new ChecklistResponse(items, ChecklistProgressResponse.of(items.size(), completed));
    }

    public ChecklistResponse add(UUID taskId, CreateChecklistItemRequest request, String username) {
        User actor = lookup.actor(username);
        Task task = lookup.manageableTask(taskId, actor);
        requireOpen(task);

        if (checklistRepository.countByTaskId(task.getId()) >= MAX_ITEMS_PER_TASK) {
            throw new IllegalArgumentException("حداکثر " + MAX_ITEMS_PER_TASK + " آیتم چک‌لیست برای هر وظیفه مجاز است");
        }

        OperationClock op = new OperationClock(clock);
        String title = request.title().trim();
        TaskChecklistItem item = newItem(task, title, checklistRepository.findMaxSortOrder(task.getId()) + 1, op);
        checklistRepository.save(item);
        activityService.record(task, actor, TaskActivityAction.CHECKLIST_ITEM_ADDED, null, title, null, op.tick());
        return list(taskId, username);
    }

    public ChecklistResponse update(UUID taskId, UUID itemId, UpdateChecklistItemRequest request, String username) {
        User actor = lookup.actor(username);
        Task task = lookup.visibleTask(taskId, actor);
        requireOpen(task);

        TaskChecklistItem item = checklistRepository.findByIdAndTaskId(itemId, task.getId())
                .orElseThrow(() -> new ResourceNotFoundException(CHECKLIST_ITEM_NOT_FOUND));
        OperationClock op = new OperationClock(clock);

        if (request.title() != null) {
            if (!policy.canManage(actor, task)) {
                throw new ForbiddenOperationException(TaskLookup.NO_PERMISSION_TO_MODIFY);
            }
            String newTitle = request.title().trim();
            if (newTitle.isEmpty()) {
                throw new IllegalArgumentException("عنوان آیتم چک‌لیست الزامی است");
            }
            item.setTitle(newTitle);
        }

        if (request.completed() != null && request.completed() != item.isCompleted()) {
            if (!policy.canManage(actor, task) && !policy.isAssignee(actor, task)) {
                throw new ForbiddenOperationException(TaskLookup.NO_PERMISSION_TO_MODIFY);
            }
            if (request.completed()) {
                item.setCompleted(true);
                item.setCompletedBy(actor);
                item.setCompletedAt(op.base());
                activityService.record(task, actor, TaskActivityAction.CHECKLIST_ITEM_COMPLETED,
                        null, item.getTitle(), null, op.tick());
            } else {
                item.setCompleted(false);
                item.setCompletedBy(null);
                item.setCompletedAt(null);
                activityService.record(task, actor, TaskActivityAction.CHECKLIST_ITEM_REOPENED,
                        null, item.getTitle(), null, op.tick());
            }
            task.setUpdatedAt(op.base());
        }

        checklistRepository.save(item);
        return list(taskId, username);
    }

    public ChecklistResponse remove(UUID taskId, UUID itemId, String username) {
        User actor = lookup.actor(username);
        Task task = lookup.manageableTask(taskId, actor);
        requireOpen(task);

        TaskChecklistItem item = checklistRepository.findByIdAndTaskId(itemId, task.getId())
                .orElseThrow(() -> new ResourceNotFoundException(CHECKLIST_ITEM_NOT_FOUND));
        OperationClock op = new OperationClock(clock);
        activityService.record(task, actor, TaskActivityAction.CHECKLIST_ITEM_REMOVED,
                item.getTitle(), null, null, op.tick());
        checklistRepository.delete(item);
        task.setUpdatedAt(op.base());
        return list(taskId, username);
    }

    /** آیتم‌های اولیه هنگام ساخت وظیفه (بدون رویداد تاریخچه‌ی جدا؛ بخشی از رویداد «ساخته شد» است) */
    void createInitialItems(Task task, List<String> titles, OperationClock op) {
        if (titles == null) {
            return;
        }
        int order = 1;
        for (String raw : titles) {
            String title = raw == null ? "" : raw.trim();
            if (title.isEmpty()) {
                continue;
            }
            checklistRepository.save(newItem(task, title, order++, op));
        }
    }

    @Transactional(readOnly = true)
    public ChecklistProgressResponse progress(UUID taskId) {
        Map<UUID, ChecklistProgressResponse> map = progressByTask(List.of(taskId));
        return map.getOrDefault(taskId, ChecklistProgressResponse.of(0, 0));
    }

    /** پیشرفت چک‌لیست چند وظیفه با یک کوئری گروهی؛ وظیفه‌ی بدون آیتم در نتیجه 0/0 است */
    @Transactional(readOnly = true)
    public Map<UUID, ChecklistProgressResponse> progressByTask(Collection<UUID> taskIds) {
        Map<UUID, ChecklistProgressResponse> result = new HashMap<>();
        if (taskIds.isEmpty()) {
            return result;
        }
        for (Object[] row : checklistRepository.summarizeByTaskIds(taskIds)) {
            UUID id = (UUID) row[0];
            long total = ((Number) row[1]).longValue();
            long completed = row[2] == null ? 0 : ((Number) row[2]).longValue();
            result.put(id, ChecklistProgressResponse.of(total, completed));
        }
        return result;
    }

    private TaskChecklistItem newItem(Task task, String title, int sortOrder, OperationClock op) {
        TaskChecklistItem item = new TaskChecklistItem();
        item.setTask(task);
        item.setTitle(title);
        item.setSortOrder(sortOrder);
        item.setCreatedAt(op.base());
        return item;
    }

    private void requireOpen(Task task) {
        if (task.getStatus().isTerminal()) {
            throw new IllegalStateException("این وظیفه بسته شده است؛ برای تغییر، ابتدا آن را بازگشایی کنید");
        }
    }
}
