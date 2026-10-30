package com.factory.productmanager.service;

import com.factory.productmanager.model.dto.PageResponse;
import com.factory.productmanager.model.dto.TaskActivityResponse;
import com.factory.productmanager.model.entity.Task;
import com.factory.productmanager.model.entity.TaskActivity;
import com.factory.productmanager.model.entity.TaskActivityAction;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.TaskActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * ثبت و خواندن تاریخچه‌ی وظیفه. فقط «افزودن» وجود دارد؛ رکورد تاریخچه هرگز ویرایش یا حذف نمی‌شود
 * (فقط با حذف خودِ وظیفه از بین می‌رود، که در این سیستم فیزیکی انجام نمی‌شود).
 */
@Service
@RequiredArgsConstructor
public class TaskActivityService {

    private static final int MAX_VALUE_LENGTH = 500;
    private static final int MAX_PAGE_SIZE = 200;

    private final TaskActivityRepository activityRepository;
    private final TaskMapper mapper;

    /** باید داخل همان تراکنشِ تغییر اصلی فراخوانی شود تا تغییر و ثبت آن اتمیک باشند */
    public void record(
            Task task,
            User actor,
            TaskActivityAction action,
            String oldValue,
            String newValue,
            String note,
            LocalDateTime at
    ) {
        TaskActivity activity = new TaskActivity();
        activity.setTask(task);
        activity.setActor(actor);
        activity.setAction(action);
        activity.setOldValue(truncate(oldValue));
        activity.setNewValue(truncate(newValue));
        activity.setNote(truncate(note));
        activity.setCreatedAt(at);
        activityRepository.save(activity);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskActivityResponse> list(Task task, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        var result = activityRepository.findByTaskId(
                task.getId(),
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")))
        );
        return PageResponse.from(result, mapper::toActivity);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH - 1) + "…";
    }
}
