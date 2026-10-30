package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.model.entity.Role;
import com.factory.productmanager.model.entity.Task;
import com.factory.productmanager.model.entity.TaskStatus;
import com.factory.productmanager.model.entity.User;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * تمام قوانین دسترسی وظایف در یک جا. این کلاس از سیستم نقش/دسترسی موجود استفاده می‌کند
 * (Role.ADMIN و Permission.MANAGE_TASKS / RECEIVE_TASKS) و نقش یا مدل دسترسی دوم نمی‌سازد.
 *
 * <ul>
 *   <li><b>ادمین</b>: همه‌ی وظایف را می‌بیند و مدیریت می‌کند.</li>
 *   <li><b>مدیر وظایف</b> (MANAGE_TASKS): وظایفی را که خودش ساخته می‌بیند و مدیریت می‌کند.</li>
 *   <li><b>مسئول وظیفه</b>: وظیفه‌ی محول‌شده به خودش را می‌بیند، وضعیت مجاز را عوض می‌کند،
 *       نظر می‌گذارد و چک‌لیست را علامت می‌زند؛ ولی ویرایش ساختاری ندارد.</li>
 * </ul>
 * حق «مدیر بودن روی وظیفه‌ی ساخته‌شده» فقط تا وقتی است که سازنده هنوز MANAGE_TASKS دارد.
 */
@Component
public class TaskAccessPolicy {

    public boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    public boolean hasManagePermission(User user) {
        return isAdmin(user) || has(user, Permission.MANAGE_TASKS);
    }

    /** آیا این کاربر می‌تواند مسئول یک وظیفه شود؟ (حساب فعال + ادمین یا دارای RECEIVE_TASKS) */
    public boolean canReceiveTasks(User user) {
        return !user.isDisabled() && (isAdmin(user) || has(user, Permission.RECEIVE_TASKS));
    }

    public boolean isAssignee(User user, Task task) {
        return task.getAssignedTo().getId().equals(user.getId());
    }

    private boolean isCreatorManager(User user, Task task) {
        return task.getCreatedBy().getId().equals(user.getId()) && hasManagePermission(user);
    }

    public boolean canView(User user, Task task) {
        if (task.getTaskType() == com.factory.productmanager.model.entity.TaskType.MELT_PASSPORT_APPROVAL) return canReviewMelt(user);
        return isAdmin(user) || isAssignee(user, task) || isCreatorManager(user, task);
    }

    public boolean canReviewMelt(User user) {
        return !user.isDisabled() && (isAdmin(user) || has(user, Permission.APPROVE_MELT_PASSPORT));
    }

    /** ویرایش ساختاری: فیلدها، مسئول، اولویت، مهلت، لغو، بازگشایی، ساختار چک‌لیست و حذف نظرها */
    public boolean canManage(User user, Task task) {
        if (task.getTaskType() == com.factory.productmanager.model.entity.TaskType.MELT_PASSPORT_APPROVAL) return isAdmin(user);
        return isAdmin(user) || isCreatorManager(user, task);
    }

    /** وضعیت‌هایی که این کاربر می‌تواند وظیفه را به آن‌ها ببرد (خالی = اجازه‌ی تغییر وضعیت ندارد) */
    public Set<TaskStatus> allowedStatusTargets(User user, Task task) {
        TaskStatus from = task.getStatus();
        Set<TaskStatus> targets = EnumSet.noneOf(TaskStatus.class);

        if (canManage(user, task)) {
            if (from.isTerminal()) {
                // وظیفه‌ی بسته‌شده فقط با «بازگشایی» به یکی از وضعیت‌های باز برمی‌گردد
                targets.addAll(TaskStatus.OPEN);
            } else {
                targets.addAll(EnumSet.allOf(TaskStatus.class));
            }
        } else if (isAssignee(user, task) && from.isOpen()) {
            // مسئول می‌تواند پیشرفت بدهد یا تکمیل کند؛ لغو و بازگشایی فقط با مدیر است
            targets.addAll(TaskStatus.OPEN);
            targets.add(TaskStatus.COMPLETED);
        }
        targets.remove(from);
        return targets;
    }

    private boolean has(User user, Permission permission) {
        return user.getPermissions() != null && user.getPermissions().contains(permission);
    }
}
