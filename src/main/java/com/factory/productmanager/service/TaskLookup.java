package com.factory.productmanager.service;

import com.factory.productmanager.exception.ForbiddenOperationException;
import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.entity.Task;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.TaskRepository;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * بارگذاری کاربر جاری و وظیفه با اعمال قاعده‌ی «عدم افشا»: اگر وظیفه وجود نداشته باشد
 * یا کاربر حق دیدنش را نداشته باشد، هر دو حالت یک پاسخ یکسان (۴۰۴) می‌گیرند تا کسی با حدس‌زدن
 * شناسه نتواند وجود وظایف دیگران را بفهمد (IDOR).
 */
@Component
@RequiredArgsConstructor
public class TaskLookup {

    public static final String TASK_NOT_FOUND = "وظیفه پیدا نشد";
    public static final String NO_PERMISSION_TO_MODIFY = "شما اجازه‌ی تغییر این وظیفه را ندارید";

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final TaskAccessPolicy policy;

    public User actor(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر پیدا نشد"));
    }

    /** وظیفه را برای کاربری که حق دیدنش را دارد برمی‌گرداند؛ در غیر این صورت ۴۰۴ */
    public Task visibleTask(UUID taskId, User actor) {
        Task task = taskRepository.findDetailedById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException(TASK_NOT_FOUND));
        if (!policy.canView(actor, task)) {
            throw new ResourceNotFoundException(TASK_NOT_FOUND);
        }
        return task;
    }

    /** وظیفه‌ی قابل‌مشاهده که کاربر حق مدیریت ساختاری آن را هم دارد؛ در غیر این صورت ۴۰۳ با پیام روشن */
    public Task manageableTask(UUID taskId, User actor) {
        Task task = visibleTask(taskId, actor);
        if (!policy.canManage(actor, task)) {
            throw new ForbiddenOperationException(NO_PERMISSION_TO_MODIFY);
        }
        return task;
    }
}
