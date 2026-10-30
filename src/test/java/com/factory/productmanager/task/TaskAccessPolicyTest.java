package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.service.TaskAccessPolicy;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** تست واحد قواعد دسترسی و جدول مجاز بودن تغییر وضعیت (بدون Spring و دیتابیس) */
class TaskAccessPolicyTest {

    private final TaskAccessPolicy policy = new TaskAccessPolicy();

    private User user(Role role, Permission... permissions) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername("u" + u.getId());
        u.setRole(role);
        u.setPermissions(new HashSet<>(Set.of(permissions)));
        return u;
    }

    private Task task(User creator, User assignee, TaskStatus status) {
        Task t = new Task();
        t.setId(UUID.randomUUID());
        t.setCreatedBy(creator);
        t.setAssignedTo(assignee);
        t.setStatus(status);
        return t;
    }

    @Test
    void visibilityRules() {
        User admin = user(Role.ADMIN);
        User manager = user(Role.OPERATOR, Permission.MANAGE_TASKS);
        User otherManager = user(Role.OPERATOR, Permission.MANAGE_TASKS);
        User worker = user(Role.OPERATOR, Permission.RECEIVE_TASKS);
        User stranger = user(Role.OPERATOR, Permission.RECEIVE_TASKS);
        Task task = task(manager, worker, TaskStatus.TODO);

        assertTrue(policy.canView(admin, task));
        assertTrue(policy.canView(manager, task));
        assertTrue(policy.canView(worker, task));
        assertFalse(policy.canView(otherManager, task));
        assertFalse(policy.canView(stranger, task));
    }

    @Test
    void creatorLosesAccessWhenManagePermissionIsRevoked() {
        User manager = user(Role.OPERATOR, Permission.MANAGE_TASKS);
        User worker = user(Role.OPERATOR, Permission.RECEIVE_TASKS);
        Task task = task(manager, worker, TaskStatus.TODO);

        manager.getPermissions().remove(Permission.MANAGE_TASKS);
        assertFalse(policy.canView(manager, task));
        assertFalse(policy.canManage(manager, task));
    }

    @Test
    void assigneeIsNeverAManagerJustByBeingAssigned() {
        User manager = user(Role.OPERATOR, Permission.MANAGE_TASKS);
        User worker = user(Role.OPERATOR, Permission.RECEIVE_TASKS);
        assertFalse(policy.canManage(worker, task(manager, worker, TaskStatus.TODO)));
    }

    @Test
    void canReceiveTasksRequiresPermissionAndEnabledAccount() {
        User withPermission = user(Role.OPERATOR, Permission.RECEIVE_TASKS);
        User without = user(Role.OPERATOR);
        User admin = user(Role.ADMIN);
        User disabled = user(Role.OPERATOR, Permission.RECEIVE_TASKS);
        disabled.setDisabled(true);

        assertTrue(policy.canReceiveTasks(withPermission));
        assertFalse(policy.canReceiveTasks(without));
        assertTrue(policy.canReceiveTasks(admin));
        assertFalse(policy.canReceiveTasks(disabled));
    }

    @Test
    void statusTargetsForAssignee() {
        User manager = user(Role.OPERATOR, Permission.MANAGE_TASKS);
        User worker = user(Role.OPERATOR, Permission.RECEIVE_TASKS);

        assertEquals(EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.WAITING, TaskStatus.COMPLETED),
                policy.allowedStatusTargets(worker, task(manager, worker, TaskStatus.TODO)));
        assertEquals(EnumSet.of(TaskStatus.TODO, TaskStatus.WAITING, TaskStatus.COMPLETED),
                policy.allowedStatusTargets(worker, task(manager, worker, TaskStatus.IN_PROGRESS)));
        // وظیفه‌ی بسته‌شده را مسئول نمی‌تواند باز کند
        assertTrue(policy.allowedStatusTargets(worker, task(manager, worker, TaskStatus.COMPLETED)).isEmpty());
        assertTrue(policy.allowedStatusTargets(worker, task(manager, worker, TaskStatus.CANCELLED)).isEmpty());
    }

    @Test
    void statusTargetsForManager() {
        User manager = user(Role.OPERATOR, Permission.MANAGE_TASKS);
        User worker = user(Role.OPERATOR, Permission.RECEIVE_TASKS);

        assertEquals(EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.WAITING, TaskStatus.COMPLETED, TaskStatus.CANCELLED),
                policy.allowedStatusTargets(manager, task(manager, worker, TaskStatus.TODO)));
        // بازگشایی فقط به وضعیت‌های باز؛ نه مستقیم از تکمیل به لغو
        assertEquals(EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.WAITING),
                policy.allowedStatusTargets(manager, task(manager, worker, TaskStatus.COMPLETED)));
        assertEquals(EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.WAITING),
                policy.allowedStatusTargets(manager, task(manager, worker, TaskStatus.CANCELLED)));
    }

    @Test
    void strangersGetNoStatusTargets() {
        User manager = user(Role.OPERATOR, Permission.MANAGE_TASKS);
        User worker = user(Role.OPERATOR, Permission.RECEIVE_TASKS);
        User stranger = user(Role.OPERATOR, Permission.RECEIVE_TASKS);
        assertTrue(policy.allowedStatusTargets(stranger, task(manager, worker, TaskStatus.TODO)).isEmpty());
    }
}
