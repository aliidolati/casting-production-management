package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.service.TaskReminderService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskCollaborationIntegrationTest extends TaskTestBase {

    @Autowired TaskReminderService reminderService;
    @Autowired Clock clock;

    // =====================================================================
    @Nested
    @DisplayName("Comments")
    class Comments {

        @Test
        void assigneeAndCreatorCanCommentAndListIsChronological() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, null);
            call(post("/api/tasks/" + id + "/comments"), worker, Map.of("content", "  شروع کردم  "))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.content").value("شروع کردم"))
                    .andExpect(jsonPath("$.author.id").value(worker.getId().toString()))
                    .andExpect(jsonPath("$.canEdit").value(true))
                    .andExpect(jsonPath("$.edited").value(false));
            call(post("/api/tasks/" + id + "/comments"), manager, Map.of("content", "ممنون"))
                    .andExpect(status().isCreated());

            call(get("/api/tasks/" + id + "/comments"), worker)
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].content").value("شروع کردم"))
                    .andExpect(jsonPath("$[1].content").value("ممنون"))
                    .andExpect(jsonPath("$[1].canEdit").value(false));
            call(get("/api/tasks/" + id), manager).andExpect(jsonPath("$.commentCount").value(2));
        }

        @Test
        void emptyOrHugeCommentIsRejected() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, null);
            call(post("/api/tasks/" + id + "/comments"), worker, Map.of("content", "   ")).andExpect(status().isBadRequest());
            call(post("/api/tasks/" + id + "/comments"), worker, Map.of("content", "ا".repeat(4001)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void onlyAuthorEditsButAuthorOrManagerDeletes() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, null);
            String created = call(post("/api/tasks/" + id + "/comments"), worker, Map.of("content", "اول"))
                    .andReturn().getResponse().getContentAsString();
            String commentId = JsonPath.read(created, "$.id");

            call(put("/api/tasks/" + id + "/comments/" + commentId), manager, Map.of("content", "دستکاری"))
                    .andExpect(status().isForbidden());
            call(put("/api/tasks/" + id + "/comments/" + commentId), worker, Map.of("content", "ویرایش‌شده"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("ویرایش‌شده"));

            // کارگر دیگر حتی وظیفه را نمی‌بیند
            call(delete("/api/tasks/" + id + "/comments/" + commentId), otherWorker).andExpect(status().isNotFound());
            // مدیر وظیفه می‌تواند حذف کند
            call(delete("/api/tasks/" + id + "/comments/" + commentId), manager).andExpect(status().isNoContent());
            call(get("/api/tasks/" + id + "/comments"), worker).andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void commentFromAnotherTaskCannotBeAccessedThroughThisTask() throws Exception {
            String taskA = createTask(manager, worker, "A", TaskPriority.LOW, null);
            String taskB = createTask(manager, worker, "B", TaskPriority.LOW, null);
            String created = call(post("/api/tasks/" + taskA + "/comments"), worker, Map.of("content", "x"))
                    .andReturn().getResponse().getContentAsString();
            String commentId = JsonPath.read(created, "$.id");

            call(put("/api/tasks/" + taskB + "/comments/" + commentId), worker, Map.of("content", "y"))
                    .andExpect(status().isNotFound());
            call(delete("/api/tasks/" + taskB + "/comments/" + commentId), manager).andExpect(status().isNotFound());
        }

        @Test
        void commentsAreStoredAsPlainTextAndLoggedInActivity() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, null);
            String xss = "<script>alert(1)</script>";
            call(post("/api/tasks/" + id + "/comments"), worker, Map.of("content", xss))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.content").value(xss));

            String body = call(get("/api/tasks/" + id + "/activities"), manager).andReturn().getResponse().getContentAsString();
            assertEquals("COMMENT_ADDED", JsonPath.<String>read(body, "$.items[0].action"));
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Checklist")
    class Checklist {

        @Test
        void managerBuildsChecklistAssigneeTogglesAndProgressUpdates() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, null);
            call(post("/api/tasks/" + id + "/checklist"), manager, Map.of("title", "گام ۱")).andExpect(status().isCreated());
            String afterSecond = call(post("/api/tasks/" + id + "/checklist"), manager, Map.of("title", "گام ۲"))
                    .andReturn().getResponse().getContentAsString();
            assertEquals(2, JsonPath.<Integer>read(afterSecond, "$.progress.total"));
            String itemId = JsonPath.read(afterSecond, "$.items[0].id");

            call(patch("/api/tasks/" + id + "/checklist/" + itemId), worker, Map.of("completed", true))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.progress.completed").value(1))
                    .andExpect(jsonPath("$.progress.percent").value(50))
                    .andExpect(jsonPath("$.items[0].completedBy.id").value(worker.getId().toString()));
            call(get("/api/tasks"), worker)
                    .andExpect(jsonPath("$.items[0].checklist.completed").value(1))
                    .andExpect(jsonPath("$.items[0].checklist.total").value(2));

            call(patch("/api/tasks/" + id + "/checklist/" + itemId), worker, Map.of("completed", false))
                    .andExpect(jsonPath("$.progress.completed").value(0));

            String body = call(get("/api/tasks/" + id + "/activities"), manager).andReturn().getResponse().getContentAsString();
            List<String> actions = JsonPath.read(body, "$.items[*].action");
            assertTrue(actions.containsAll(List.of(
                    "CHECKLIST_ITEM_ADDED", "CHECKLIST_ITEM_COMPLETED", "CHECKLIST_ITEM_REOPENED")), actions.toString());
        }

        @Test
        void assigneeCannotAddRemoveOrRenameItems() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, null);
            String created = call(post("/api/tasks/" + id + "/checklist"), manager, Map.of("title", "گام"))
                    .andReturn().getResponse().getContentAsString();
            String itemId = JsonPath.read(created, "$.items[0].id");

            call(post("/api/tasks/" + id + "/checklist"), worker, Map.of("title", "تازه")).andExpect(status().isForbidden());
            call(delete("/api/tasks/" + id + "/checklist/" + itemId), worker).andExpect(status().isForbidden());
            call(patch("/api/tasks/" + id + "/checklist/" + itemId), worker, Map.of("title", "عوض"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void outsiderAndOtherTasksItemsAreNotReachable() throws Exception {
            String taskA = createTask(manager, worker, "A", TaskPriority.LOW, null);
            String taskB = createTask(manager, worker, "B", TaskPriority.LOW, null);
            String created = call(post("/api/tasks/" + taskA + "/checklist"), manager, Map.of("title", "گام"))
                    .andReturn().getResponse().getContentAsString();
            String itemId = JsonPath.read(created, "$.items[0].id");

            call(patch("/api/tasks/" + taskA + "/checklist/" + itemId), otherWorker, Map.of("completed", true))
                    .andExpect(status().isNotFound());
            call(patch("/api/tasks/" + taskB + "/checklist/" + itemId), worker, Map.of("completed", true))
                    .andExpect(status().isNotFound());
            call(delete("/api/tasks/" + taskB + "/checklist/" + itemId), manager).andExpect(status().isNotFound());
        }

        @Test
        void closedTaskRejectsChecklistChanges() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, null);
            call(post("/api/tasks/" + id + "/checklist"), manager, Map.of("title", "گام")).andExpect(status().isCreated());
            call(patch("/api/tasks/" + id + "/status"), worker, Map.of("status", "COMPLETED")).andExpect(status().isOk());
            call(post("/api/tasks/" + id + "/checklist"), manager, Map.of("title", "دیرهنگام")).andExpect(status().isBadRequest());
        }

        @Test
        void blankTitleAndItemLimitAreEnforced() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, null);
            call(post("/api/tasks/" + id + "/checklist"), manager, Map.of("title", " ")).andExpect(status().isBadRequest());
            for (int i = 0; i < 50; i++) {
                call(post("/api/tasks/" + id + "/checklist"), manager, Map.of("title", "آیتم " + i))
                        .andExpect(status().isCreated());
            }
            call(post("/api/tasks/" + id + "/checklist"), manager, Map.of("title", "آیتم اضافه")).andExpect(status().isBadRequest());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Notifications")
    class Notifications {

        private String feed(User user) throws Exception {
            return call(get("/api/notifications"), user).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        }

        private int unread(User user) throws Exception {
            String body = call(get("/api/notifications/unread-count"), user).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            return JsonPath.<Number>read(body, "$.unreadCount").intValue();
        }

        @Test
        void assigneeIsNotifiedButActorIsNot() throws Exception {
            String id = createTask(manager, worker, "جدید", TaskPriority.LOW, null);
            assertEquals(1, unread(worker));
            assertEquals(0, unread(manager));

            String body = feed(worker);
            assertEquals("TASK_ASSIGNED", JsonPath.<String>read(body, "$.items[0].type"));
            assertEquals(id, JsonPath.<String>read(body, "$.items[0].taskId"));
            assertEquals(false, JsonPath.<Boolean>read(body, "$.items[0].read"));
        }

        @Test
        void selfAssignmentCreatesNoNotification() throws Exception {
            createTask(manager, manager, "برای خودم", TaskPriority.LOW, null);
            assertEquals(0, unread(manager));
        }

        @Test
        void markReadAndMarkAllRead() throws Exception {
            createTask(manager, worker, "یک", TaskPriority.LOW, null);
            createTask(manager, worker, "دو", TaskPriority.LOW, null);
            assertEquals(2, unread(worker));

            String notificationId = JsonPath.read(feed(worker), "$.items[0].id");
            call(post("/api/notifications/" + notificationId + "/read"), worker).andExpect(status().is2xxSuccessful());
            assertEquals(1, unread(worker));

            call(post("/api/notifications/read-all"), worker).andExpect(status().is2xxSuccessful());
            assertEquals(0, unread(worker));
        }

        @Test
        void otherUsersCannotReadOrDismissMyNotifications() throws Exception {
            createTask(manager, worker, "خصوصی", TaskPriority.LOW, null);
            String notificationId = JsonPath.read(feed(worker), "$.items[0].id");

            assertEquals("[]", JsonPath.<List<?>>read(feed(otherWorker), "$.items").toString());
            call(post("/api/notifications/" + notificationId + "/read"), otherWorker).andExpect(status().isNotFound());
            call(delete("/api/notifications/" + notificationId), otherWorker).andExpect(status().isNotFound());
            assertEquals(1, unread(worker));
        }

        @Test
        void tensileBroadcastNotificationsAreNotVisibleToRegularUsers() throws Exception {
            SystemNotification broadcast = new SystemNotification();
            broadcast.setType(NotificationType.TENSILE_AUTO_APPROVED);
            broadcast.setMessage("تست کشش تأیید شد");
            broadcast.setPieceName("قطعه");
            broadcast.setPieceNumber(1);
            broadcast.setProjectCode("P");
            broadcast.setCreatedAt(LocalDateTime.now());
            notificationRepository.saveAndFlush(broadcast);

            assertEquals(0, unread(worker));
            assertEquals(1, unread(admin));
        }

        @Test
        void repeatedUpdatesCoalesceIntoOneUnreadNotification() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, dueIn(3));
            call(post("/api/notifications/read-all"), worker).andExpect(status().is2xxSuccessful());

            call(patch("/api/tasks/" + id + "/priority"), manager, Map.of("priority", "HIGH")).andExpect(status().isOk());
            call(patch("/api/tasks/" + id + "/priority"), manager, Map.of("priority", "CRITICAL")).andExpect(status().isOk());
            call(patch("/api/tasks/" + id + "/deadline"), manager, Map.of("dueDate", dueIn(6))).andExpect(status().isOk());

            assertEquals(1, unread(worker), "تغییرهای پشت‌سرهم نباید اعلان تکراری بسازند");
        }

        @Test
        void commentNotifiesTheOtherPartyOnly() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, null);
            call(post("/api/notifications/read-all"), worker).andExpect(status().is2xxSuccessful());

            call(post("/api/tasks/" + id + "/comments"), worker, Map.of("content", "سلام")).andExpect(status().isCreated());
            assertEquals(0, unread(worker));
            assertEquals("TASK_COMMENT", JsonPath.<String>read(feed(manager), "$.items[0].type"));
        }

        @Test
        void completionAndReopenNotifyTheCreatorAndAssignee() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, null);
            call(patch("/api/tasks/" + id + "/status"), worker, Map.of("status", "COMPLETED")).andExpect(status().isOk());
            assertEquals("TASK_COMPLETED", JsonPath.<String>read(feed(manager), "$.items[0].type"));

            call(post("/api/notifications/read-all"), worker).andExpect(status().is2xxSuccessful());
            call(patch("/api/tasks/" + id + "/status"), manager, Map.of("status", "TODO", "reason", "ناقص بود"))
                    .andExpect(status().isOk());
            assertEquals("TASK_REOPENED", JsonPath.<String>read(feed(worker), "$.items[0].type"));
        }

        @Test
        void reassignmentNotifiesBothOldAndNewAssignee() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, null);
            call(post("/api/notifications/read-all"), worker).andExpect(status().is2xxSuccessful());

            call(patch("/api/tasks/" + id + "/assignee"), manager, Map.of("assigneeId", otherWorker.getId()))
                    .andExpect(status().isOk());

            assertEquals("TASK_ASSIGNED", JsonPath.<String>read(feed(otherWorker), "$.items[0].type"));
            assertEquals("TASK_UPDATED", JsonPath.<String>read(feed(worker), "$.items[0].type"));
            // مسئول قبلی دیگر وظیفه را نمی‌بیند
            call(get("/api/tasks/" + id), worker).andExpect(status().isNotFound());
        }

        @Test
        void deletingTaskRowCascadesToItsNotifications() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, null);
            assertEquals(1, unread(worker));
            taskRepository.deleteById(UUID.fromString(id));
            taskRepository.flush();
            notificationRepository.flush();
            // ردیف اعلان یتیم نمی‌ماند (ON DELETE CASCADE روی task_id)
            assertEquals(0, notificationRepository.findAll().stream()
                    .filter(n -> n.getTask() != null && id.equals(n.getTask().getId().toString())).count());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Deadline reminders")
    class Reminders {

        private long remindersOfType(User user, NotificationType type) {
            return notificationRepository.findAll().stream()
                    .filter(n -> n.getRecipient() != null && n.getRecipient().getId().equals(user.getId()))
                    .filter(n -> n.getType() == type).count();
        }

        private void setDue(String taskId, LocalDateTime due) {
            Task task = reloadTask(taskId);
            task.setDueDate(due);
            taskRepository.saveAndFlush(task);
        }

        @Test
        void createsOverdueTodayAndTomorrowRemindersExactlyOnce() throws Exception {
            LocalDateTime now = LocalDateTime.now(clock);
            String overdue = createTask(manager, worker, "گذشته", TaskPriority.LOW, dueIn(3));
            String today = createTask(manager, worker, "امروز", TaskPriority.LOW, dueIn(3));
            String tomorrow = createTask(manager, worker, "فردا", TaskPriority.LOW, dueIn(3));
            String far = createTask(manager, worker, "دور", TaskPriority.LOW, dueIn(10));

            setDue(overdue, now.minusHours(5));
            // «امروز» باید هنوز نگذشته باشد؛ اگر نزدیک نیمه‌شب تست اجرا شود به همان لحظه‌ی بعد می‌رود
            LocalDateTime endOfToday = now.toLocalDate().plusDays(1).atStartOfDay();
            setDue(today, now.plusMinutes(30).isBefore(endOfToday) ? now.plusMinutes(30) : endOfToday.minusSeconds(1));
            setDue(tomorrow, endOfToday.plusHours(10));

            int first = reminderService.sendReminders();
            assertEquals(3, first);
            assertEquals(1, remindersOfType(worker, NotificationType.TASK_OVERDUE));
            assertEquals(1, remindersOfType(worker, NotificationType.TASK_DUE_TODAY));
            assertEquals(1, remindersOfType(worker, NotificationType.TASK_DUE_TOMORROW));

            // اجرای دوباره هیچ اعلان تکراری نمی‌سازد (ضد اسپم)
            assertEquals(0, reminderService.sendReminders());
            assertNotNull(far);
        }

        @Test
        void closedTasksGetNoReminders() throws Exception {
            String id = createTask(manager, worker, "بسته", TaskPriority.LOW, dueIn(3));
            call(patch("/api/tasks/" + id + "/status"), worker, Map.of("status", "COMPLETED")).andExpect(status().isOk());
            setDue(id, LocalDateTime.now(clock).minusHours(2));
            assertEquals(0, reminderService.sendReminders());
        }

        @Test
        void changingDeadlineReEnablesReminderForTheNewDeadline() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, dueIn(3));
            setDue(id, LocalDateTime.now(clock).minusHours(2));
            assertEquals(1, reminderService.sendReminders());
            assertEquals(0, reminderService.sendReminders());

            setDue(id, LocalDateTime.now(clock).minusHours(1).withSecond(0).withNano(0));
            assertEquals(1, reminderService.sendReminders());
        }
    }
}
