package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskApiIntegrationTest extends TaskTestBase {

    // =====================================================================
    // احراز هویت و دسترسی
    // =====================================================================
    @Nested
    @DisplayName("Authentication & authorization")
    class Security {

        @Test
        void unauthenticatedRequestsAreRejected() throws Exception {
            mvc.perform(get("/api/tasks")).andExpect(status().is(anyOf(is(401), is(403))));
            mvc.perform(get("/api/tasks/" + UUID.randomUUID())).andExpect(status().is(anyOf(is(401), is(403))));
            mvc.perform(post("/api/tasks")).andExpect(status().is(anyOf(is(401), is(403))));
        }

        @Test
        void userWithoutManagePermissionCannotCreateOrEdit() throws Exception {
            call(post("/api/tasks"), worker, taskBody("x", worker, TaskPriority.LOW, null))
                    .andExpect(status().isForbidden());

            String id = createTask(manager, worker, "کار", TaskPriority.MEDIUM, dueIn(2));
            call(put("/api/tasks/" + id), worker, Map.of(
                    "title", "هک", "assigneeId", worker.getId(), "priority", "LOW"))
                    .andExpect(status().isForbidden());
            call(patch("/api/tasks/" + id + "/priority"), worker, Map.of("priority", "CRITICAL"))
                    .andExpect(status().isForbidden());
            call(patch("/api/tasks/" + id + "/assignee"), worker, Map.of("assigneeId", otherWorker.getId()))
                    .andExpect(status().isForbidden());
            call(delete("/api/tasks/" + id), worker).andExpect(status().isForbidden());
            call(get("/api/tasks/assignees"), worker).andExpect(status().isForbidden());
        }

        @Test
        void taskOfSomeoneElseIsNotFoundNotForbidden() throws Exception {
            String id = createTask(manager, worker, "خصوصی", TaskPriority.MEDIUM, dueIn(2));
            String missing = UUID.randomUUID().toString();

            // وظیفه‌ی موجودِ غیرقابل‌دیدن و وظیفه‌ی ناموجود باید دقیقاً یک پاسخ بدهند (ضد IDOR)
            String hidden = call(get("/api/tasks/" + id), otherWorker).andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();
            String absent = call(get("/api/tasks/" + missing), otherWorker).andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();
            assertEquals(JsonPath.<String>read(hidden, "$.message"), JsonPath.<String>read(absent, "$.message"));

            call(patch("/api/tasks/" + id + "/status"), otherWorker, Map.of("status", "COMPLETED"))
                    .andExpect(status().isNotFound());
            call(get("/api/tasks/" + id + "/comments"), otherWorker).andExpect(status().isNotFound());
            call(get("/api/tasks/" + id + "/activities"), otherWorker).andExpect(status().isNotFound());
            call(get("/api/tasks/" + id + "/checklist"), otherWorker).andExpect(status().isNotFound());
            call(post("/api/tasks/" + id + "/comments"), otherWorker, Map.of("content", "سلام"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anotherManagerCannotSeeOrEditTasksTheyDidNotCreate() throws Exception {
            String id = createTask(manager, worker, "کار مدیر اول", TaskPriority.HIGH, dueIn(3));

            call(get("/api/tasks/" + id), otherManager).andExpect(status().isNotFound());
            call(put("/api/tasks/" + id), otherManager, Map.of(
                    "title", "تغییر", "assigneeId", worker.getId(), "priority", "LOW"))
                    .andExpect(status().isNotFound());
            call(delete("/api/tasks/" + id), otherManager).andExpect(status().isNotFound());
            call(get("/api/tasks"), otherManager).andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void adminSeesEverythingAndAssigneeSeesOnlyOwn() throws Exception {
            createTask(manager, worker, "برای کارگر ۱", TaskPriority.MEDIUM, dueIn(2));
            createTask(manager, otherWorker, "برای کارگر ۲", TaskPriority.MEDIUM, dueIn(2));

            call(get("/api/tasks").queryParam("q", "کارگر"), admin).andExpect(jsonPath("$.totalElements").value(2));
            call(get("/api/tasks"), worker).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.items[0].title").value("برای کارگر ۱"));
            call(get("/api/tasks"), manager).andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void disabledUserCannotLogIn() throws Exception {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                            .content(json.writeValueAsString(Map.of(
                                    "username", disabledWorker.getUsername(), "password", PASSWORD))))
                    .andExpect(status().is4xxClientError());
        }
    }

    // =====================================================================
    // ساخت و اعتبارسنجی
    // =====================================================================
    @Nested
    @DisplayName("Create & validation")
    class CreateAndValidation {

        @Test
        void managerCreatesTaskForWorker() throws Exception {
            call(post("/api/tasks"), manager, taskBody("  بازرسی قطعه  ", worker, TaskPriority.HIGH, dueIn(2)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("بازرسی قطعه"))
                    .andExpect(jsonPath("$.status").value("TODO"))
                    .andExpect(jsonPath("$.priority").value("HIGH"))
                    .andExpect(jsonPath("$.assignee.id").value(worker.getId().toString()))
                    .andExpect(jsonPath("$.creator.id").value(manager.getId().toString()))
                    .andExpect(jsonPath("$.overdue").value(false))
                    .andExpect(jsonPath("$.permissions.canEdit").value(true));
        }

        @Test
        void creationIsRecordedInActivityLogInOrder() throws Exception {
            String id = createTask(manager, worker, "ترتیب", TaskPriority.LOW, null);
            String body = call(get("/api/tasks/" + id + "/activities"), manager).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<String> actions = JsonPath.read(body, "$.items[*].action");
            // جدیدترین اول: ASSIGNED بعد از CREATED رخ داده است
            assertEquals(List.of("ASSIGNED", "CREATED"), actions);
        }

        @Test
        void invalidInputIsRejectedWith400() throws Exception {
            call(post("/api/tasks"), manager, taskBody("", worker, TaskPriority.LOW, null))
                    .andExpect(status().isBadRequest());
            call(post("/api/tasks"), manager, taskBody("ا".repeat(201), worker, TaskPriority.LOW, null))
                    .andExpect(status().isBadRequest());

            Map<String, Object> badPriority = taskBody("x", worker, TaskPriority.LOW, null);
            badPriority.put("priority", "URGENT");
            call(post("/api/tasks"), manager, badPriority).andExpect(status().isBadRequest());

            Map<String, Object> noAssignee = taskBody("x", worker, TaskPriority.LOW, null);
            noAssignee.remove("assigneeId");
            call(post("/api/tasks"), manager, noAssignee).andExpect(status().isBadRequest());
        }

        @Test
        void malformedJsonAndBadQueryParamsGive400Not500() throws Exception {
            call(post("/api/tasks").contentType("application/json").content("{not json"), manager)
                    .andExpect(status().isBadRequest());
            call(get("/api/tasks").queryParam("status", "NOPE"), manager).andExpect(status().isBadRequest());
            call(get("/api/tasks").queryParam("sort", "bogus"), manager).andExpect(status().isBadRequest());
            call(get("/api/tasks/not-a-uuid"), manager).andExpect(status().isBadRequest());
        }

        @Test
        void pastOrAbsurdDeadlineIsRejected() throws Exception {
            call(post("/api/tasks"), manager, taskBody("x", worker, TaskPriority.LOW, dueIn(-1)))
                    .andExpect(status().isBadRequest());
            call(post("/api/tasks"), manager, taskBody("x", worker, TaskPriority.LOW, dueIn(365 * 20)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void ineligibleAssigneesAreRejectedWithTheSameMessage() throws Exception {
            String noPermission = call(post("/api/tasks"), manager, taskBody("x", outsider, TaskPriority.LOW, null))
                    .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
            String disabled = call(post("/api/tasks"), manager, taskBody("x", disabledWorker, TaskPriority.LOW, null))
                    .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();

            Map<String, Object> ghost = taskBody("x", worker, TaskPriority.LOW, null);
            ghost.put("assigneeId", UUID.randomUUID());
            String missing = call(post("/api/tasks"), manager, ghost)
                    .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();

            // نمی‌شود از روی پیام فهمید کاربر وجود دارد یا غیرفعال است
            assertEquals(JsonPath.<String>read(noPermission, "$.message"), JsonPath.<String>read(disabled, "$.message"));
            assertEquals(JsonPath.<String>read(noPermission, "$.message"), JsonPath.<String>read(missing, "$.message"));
        }

        @Test
        void assigneeListContainsOnlyEligibleUsersAndNoSensitiveData() throws Exception {
            String body = call(get("/api/tasks/assignees"), manager).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<String> usernames = JsonPath.read(body, "$[*].username");
            assertTrue(usernames.contains(worker.getUsername()));
            assertFalse(usernames.contains(outsider.getUsername()));
            assertFalse(usernames.contains(disabledWorker.getUsername()));
            assertFalse(body.contains("password"));
            assertFalse(body.contains("permissions"));
        }

        @Test
        void initialChecklistIsCreatedWithTask() throws Exception {
            Map<String, Object> body = taskBody("با چک‌لیست", worker, TaskPriority.MEDIUM, null);
            body.put("checklist", List.of("گام اول", "گام دوم"));
            call(post("/api/tasks"), manager, body).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.checklist.total").value(2))
                    .andExpect(jsonPath("$.checklist.completed").value(0));

            // آیتم خالی در چک‌لیست اولیه پذیرفته نمی‌شود
            Map<String, Object> blank = taskBody("با آیتم خالی", worker, TaskPriority.MEDIUM, null);
            blank.put("checklist", List.of("گام اول", "  "));
            call(post("/api/tasks"), manager, blank).andExpect(status().isBadRequest());
        }
    }

    // =====================================================================
    // وضعیت و چرخه‌ی عمر
    // =====================================================================
    @Nested
    @DisplayName("Status workflow")
    class StatusWorkflow {

        @Test
        void assigneeStartsAndCompletesTask() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.MEDIUM, dueIn(2));

            call(patch("/api/tasks/" + id + "/status"), worker, Map.of("status", "IN_PROGRESS"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
            call(patch("/api/tasks/" + id + "/status"), worker, Map.of("status", "COMPLETED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.completedAt", notNullValue()))
                    .andExpect(jsonPath("$.overdue").value(false));
        }

        @Test
        void assigneeCannotCancelOrReopen() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.MEDIUM, dueIn(2));
            call(patch("/api/tasks/" + id + "/status"), worker, Map.of("status", "CANCELLED"))
                    .andExpect(status().isForbidden());

            call(patch("/api/tasks/" + id + "/status"), worker, Map.of("status", "COMPLETED")).andExpect(status().isOk());
            call(patch("/api/tasks/" + id + "/status"), worker, Map.of("status", "TODO"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void managerCanReopenWithReasonAndHistoryIsKept() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.MEDIUM, dueIn(2));
            call(patch("/api/tasks/" + id + "/status"), worker, Map.of("status", "COMPLETED")).andExpect(status().isOk());

            call(patch("/api/tasks/" + id + "/status"), manager, Map.of("status", "IN_PROGRESS", "reason", "نیاز به بازبینی"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.completedAt").doesNotExist());

            String body = call(get("/api/tasks/" + id + "/activities"), manager).andReturn().getResponse().getContentAsString();
            List<String> actions = JsonPath.read(body, "$.items[*].action");
            assertEquals(List.of("REOPENED", "COMPLETED", "ASSIGNED", "CREATED"), actions);
            assertEquals("نیاز به بازبینی", JsonPath.<String>read(body, "$.items[0].note"));
            assertEquals("COMPLETED", JsonPath.<String>read(body, "$.items[0].oldValue"));
            assertEquals("IN_PROGRESS", JsonPath.<String>read(body, "$.items[0].newValue"));
        }

        @Test
        void sameStatusAndClosedTaskEditsAreRejected() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.MEDIUM, dueIn(2));
            call(patch("/api/tasks/" + id + "/status"), worker, Map.of("status", "TODO"))
                    .andExpect(status().isBadRequest());

            call(delete("/api/tasks/" + id), manager).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
            call(delete("/api/tasks/" + id), manager).andExpect(status().isBadRequest());
            call(patch("/api/tasks/" + id + "/priority"), manager, Map.of("priority", "HIGH"))
                    .andExpect(status().isBadRequest());
            call(put("/api/tasks/" + id), manager, Map.of(
                    "title", "جدید", "assigneeId", worker.getId(), "priority", "LOW"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void cancelKeepsTaskAndHistory() throws Exception {
            String id = createTask(manager, worker, "قابل لغو", TaskPriority.MEDIUM, dueIn(2));
            call(delete("/api/tasks/" + id).queryParam("reason", "دیگر لازم نیست"), manager).andExpect(status().isOk());

            assertTrue(taskRepository.existsById(UUID.fromString(id)), "حذف باید لغو باشد نه پاک‌شدن");
            String body = call(get("/api/tasks/" + id + "/activities"), manager).andReturn().getResponse().getContentAsString();
            assertEquals("CANCELLED", JsonPath.<String>read(body, "$.items[0].action"));
            assertEquals("دیگر لازم نیست", JsonPath.<String>read(body, "$.items[0].note"));
        }

        @Test
        void completedTaskCannotBeCancelled() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.MEDIUM, dueIn(2));
            call(patch("/api/tasks/" + id + "/status"), worker, Map.of("status", "COMPLETED")).andExpect(status().isOk());
            call(delete("/api/tasks/" + id), manager).andExpect(status().isBadRequest());
        }
    }

    // =====================================================================
    // ویرایش و تاریخچه‌ی فیلدها
    // =====================================================================
    @Nested
    @DisplayName("Field changes & audit")
    class FieldChanges {

        @Test
        void everyAuditedFieldChangeIsLogged() throws Exception {
            String id = createTask(manager, worker, "قدیمی", TaskPriority.LOW, dueIn(2));

            call(patch("/api/tasks/" + id + "/priority"), manager, Map.of("priority", "CRITICAL")).andExpect(status().isOk());
            call(patch("/api/tasks/" + id + "/deadline"), manager, Map.of("dueDate", dueIn(5))).andExpect(status().isOk());
            call(patch("/api/tasks/" + id + "/assignee"), manager, Map.of("assigneeId", otherWorker.getId()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.assignee.id").value(otherWorker.getId().toString()));
            call(put("/api/tasks/" + id), manager, Map.of(
                    "title", "جدید", "description", "توضیح", "assigneeId", otherWorker.getId(), "priority", "CRITICAL"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("جدید"));

            String body = call(get("/api/tasks/" + id + "/activities"), manager).andReturn().getResponse().getContentAsString();
            List<String> actions = JsonPath.read(body, "$.items[*].action");
            assertTrue(actions.containsAll(List.of(
                    "CREATED", "ASSIGNED", "PRIORITY_CHANGED", "DEADLINE_CHANGED",
                    "ASSIGNEE_CHANGED", "TITLE_CHANGED", "DESCRIPTION_CHANGED")), actions.toString());
            // PUT با مقدارهای بدون تغییر (اولویت CRITICAL و مسئول یکسان) رویداد اضافه نمی‌سازد
            assertEquals(1, actions.stream().filter("PRIORITY_CHANGED"::equals).count());
            assertEquals(1, actions.stream().filter("ASSIGNEE_CHANGED"::equals).count());
            assertEquals(List.of("LOW"), JsonPath.<List<String>>read(body, "$.items[?(@.action=='PRIORITY_CHANGED')].oldValue"));
            assertEquals(List.of("CRITICAL"), JsonPath.<List<String>>read(body, "$.items[?(@.action=='PRIORITY_CHANGED')].newValue"));
        }

        @Test
        void deadlineCanBeClearedAndIsLogged() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, dueIn(2));
            call(patch("/api/tasks/" + id + "/deadline"), manager, new java.util.HashMap<String, Object>() {{
                put("dueDate", null);
            }}).andExpect(status().isOk()).andExpect(jsonPath("$.dueDate").doesNotExist())
                    .andExpect(jsonPath("$.dueInMinutes").doesNotExist());
        }

        @Test
        void unchangedUpdateDoesNotTouchUpdatedAtOrLog() throws Exception {
            String id = createTask(manager, worker, "ثابت", TaskPriority.LOW, null);
            LocalDateTime before = reloadTask(id).getUpdatedAt();
            call(put("/api/tasks/" + id), manager, Map.of(
                    "title", "ثابت", "assigneeId", worker.getId(), "priority", "LOW")).andExpect(status().isOk());
            assertEquals(before, reloadTask(id).getUpdatedAt());
            String body = call(get("/api/tasks/" + id + "/activities"), manager).andReturn().getResponse().getContentAsString();
            assertEquals(2, JsonPath.<List<?>>read(body, "$.items").size());
        }

        @Test
        void reassigningToIneligibleUserFailsAndKeepsAssignee() throws Exception {
            String id = createTask(manager, worker, "کار", TaskPriority.LOW, null);
            call(patch("/api/tasks/" + id + "/assignee"), manager, Map.of("assigneeId", outsider.getId()))
                    .andExpect(status().isBadRequest());
            assertEquals(worker.getId(), reloadTask(id).getAssignedTo().getId());
        }
    }

    // =====================================================================
    // ارتباط با پروژه/قطعه
    // =====================================================================
    @Nested
    @DisplayName("Project & part relations")
    class Relations {

        @Test
        void taskCanBeLinkedToPartAndProjectIsDerived() throws Exception {
            Piece piece = newPiece("PRJ-" + UUID.randomUUID().toString().substring(0, 6), "بدنه پمپ", 7);
            Map<String, Object> body = taskBody("بازرسی", worker, TaskPriority.MEDIUM, null);
            body.put("partId", piece.getId());

            call(post("/api/tasks"), manager, body).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.part.id").value(piece.getId().toString()))
                    .andExpect(jsonPath("$.part.pieceName").value("بدنه پمپ"))
                    .andExpect(jsonPath("$.part.pieceNumber").value(7))
                    .andExpect(jsonPath("$.project.id").value(piece.getPieceDefinition().getProject().getId().toString()));
        }

        @Test
        void partFromAnotherProjectIsRejected() throws Exception {
            Piece piece = newPiece("A-" + UUID.randomUUID().toString().substring(0, 6), "قطعه", 1);
            Piece foreign = newPiece("B-" + UUID.randomUUID().toString().substring(0, 6), "قطعه دیگر", 1);
            Map<String, Object> body = taskBody("x", worker, TaskPriority.LOW, null);
            body.put("partId", piece.getId());
            body.put("projectId", foreign.getPieceDefinition().getProject().getId());
            call(post("/api/tasks"), manager, body).andExpect(status().isBadRequest());
        }

        @Test
        void unknownProjectOrPartIsRejected() throws Exception {
            Map<String, Object> badProject = taskBody("x", worker, TaskPriority.LOW, null);
            badProject.put("projectId", UUID.randomUUID());
            call(post("/api/tasks"), manager, badProject).andExpect(status().isBadRequest());

            Map<String, Object> badPart = taskBody("x", worker, TaskPriority.LOW, null);
            badPart.put("partId", UUID.randomUUID());
            call(post("/api/tasks"), manager, badPart).andExpect(status().isBadRequest());
        }

        @Test
        void archivedPartCannotBeLinked() throws Exception {
            Piece piece = newPiece("ARC-" + UUID.randomUUID().toString().substring(0, 6), "بایگانی", 1);
            piece.setArchivedAt(LocalDateTime.now());
            pieceRepository.saveAndFlush(piece);
            Map<String, Object> body = taskBody("x", worker, TaskPriority.LOW, null);
            body.put("partId", piece.getId());
            call(post("/api/tasks"), manager, body).andExpect(status().isBadRequest());
        }

        @Test
        void filteringByPartAndProjectWorks() throws Exception {
            Piece piece = newPiece("F-" + UUID.randomUUID().toString().substring(0, 6), "قطعه فیلتر", 3);
            Map<String, Object> linked = taskBody("مرتبط", worker, TaskPriority.LOW, null);
            linked.put("partId", piece.getId());
            call(post("/api/tasks"), manager, linked).andExpect(status().isCreated());
            createTask(manager, worker, "نامرتبط", TaskPriority.LOW, null);

            call(get("/api/tasks").queryParam("partId", piece.getId().toString()), manager)
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.items[0].title").value("مرتبط"));
            call(get("/api/tasks").queryParam("projectId",
                    piece.getPieceDefinition().getProject().getId().toString()), manager)
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void partLookupIsRestrictedAndLimited() throws Exception {
            Piece piece = newPiece("L-" + UUID.randomUUID().toString().substring(0, 6), "جستجوی قطعه", 12);
            call(get("/api/tasks/lookup/parts").queryParam("q", "جستجوی"), worker).andExpect(status().isForbidden());
            call(get("/api/tasks/lookup/parts").queryParam("q", "جستجوی"), manager)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(piece.getId().toString()));
        }
    }

    // =====================================================================
    // لیست: فیلتر، جستجو، مرتب‌سازی، صفحه‌بندی
    // =====================================================================
    @Nested
    @DisplayName("List: filtering, search, sorting, pagination")
    class Listing {

        @Test
        void paginationReturnsStablePagesAndMetadata() throws Exception {
            for (int i = 1; i <= 5; i++) {
                createTask(manager, worker, "کار " + i, TaskPriority.MEDIUM, dueIn(i));
            }
            String p0 = call(get("/api/tasks").queryParam("size", "2").queryParam("page", "0"), manager)
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andReturn().getResponse().getContentAsString();
            String p2 = call(get("/api/tasks").queryParam("size", "2").queryParam("page", "2"), manager)
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andReturn().getResponse().getContentAsString();
            String p1 = call(get("/api/tasks").queryParam("size", "2").queryParam("page", "1"), manager)
                    .andReturn().getResponse().getContentAsString();

            List<String> all = new java.util.ArrayList<>();
            all.addAll(JsonPath.read(p0, "$.items[*].id"));
            all.addAll(JsonPath.read(p1, "$.items[*].id"));
            all.addAll(JsonPath.read(p2, "$.items[*].id"));
            assertEquals(5, all.stream().distinct().count(), "صفحه‌ها نباید تکراری یا ناقص باشند");
        }

        @Test
        void oversizedPageSizeIsClamped() throws Exception {
            createTask(manager, worker, "کار", TaskPriority.MEDIUM, null);
            call(get("/api/tasks").queryParam("size", "100000"), manager)
                    .andExpect(jsonPath("$.size").value(100));
            call(get("/api/tasks").queryParam("size", "-5").queryParam("page", "-3"), manager)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0));
        }

        @Test
        void filtersByStatusPriorityAssigneeAndCreator() throws Exception {
            String a = createTask(manager, worker, "الف", TaskPriority.LOW, null);
            createTask(manager, otherWorker, "ب", TaskPriority.HIGH, null);
            call(patch("/api/tasks/" + a + "/status"), worker, Map.of("status", "IN_PROGRESS")).andExpect(status().isOk());

            call(get("/api/tasks").queryParam("status", "IN_PROGRESS"), manager)
                    .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].title").value("الف"));
            call(get("/api/tasks").queryParam("status", "TODO").queryParam("status", "IN_PROGRESS"), manager)
                    .andExpect(jsonPath("$.totalElements").value(2));
            call(get("/api/tasks").queryParam("priority", "HIGH"), manager)
                    .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].title").value("ب"));
            call(get("/api/tasks").queryParam("assigneeId", otherWorker.getId().toString()), manager)
                    .andExpect(jsonPath("$.totalElements").value(1));
            call(get("/api/tasks").queryParam("creatorId", manager.getId().toString()), admin)
                    .andExpect(jsonPath("$.totalElements").value(2));
            call(get("/api/tasks").queryParam("mine", "true"), manager).andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void filtersByDueDateRange() throws Exception {
            createTask(manager, worker, "نزدیک", TaskPriority.LOW, dueIn(1));
            createTask(manager, worker, "دور", TaskPriority.LOW, dueIn(20));
            createTask(manager, worker, "بدون مهلت", TaskPriority.LOW, null);

            String from = LocalDateTime.now().plusDays(10).toLocalDate().toString();
            String to = LocalDateTime.now().plusDays(30).toLocalDate().toString();
            call(get("/api/tasks").queryParam("dueFrom", from).queryParam("dueTo", to), manager)
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.items[0].title").value("دور"));
            call(get("/api/tasks").queryParam("dueDate", LocalDateTime.now().plusDays(1).toLocalDate().toString()), manager)
                    .andExpect(jsonPath("$.items[0].title").value("نزدیک"));
        }

        @Test
        void overdueIsComputedNotStoredAndFilterable() throws Exception {
            String id = createTask(manager, worker, "دیرکرد", TaskPriority.LOW, dueIn(2));
            createTask(manager, worker, "به‌موقع", TaskPriority.LOW, dueIn(2));

            Task task = reloadTask(id);
            task.setDueDate(LocalDateTime.now().minusDays(2));
            taskRepository.saveAndFlush(task);

            call(get("/api/tasks/" + id), worker)
                    .andExpect(jsonPath("$.overdue").value(true))
                    .andExpect(jsonPath("$.dueInMinutes", lessThan(0)));
            call(get("/api/tasks").queryParam("overdue", "true"), manager)
                    .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].title").value("دیرکرد"));
            call(get("/api/tasks").queryParam("overdue", "false"), manager)
                    .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].title").value("به‌موقع"));

            // وقتی تکمیل شد دیگر دیرکرد حساب نمی‌شود
            call(patch("/api/tasks/" + id + "/status"), worker, Map.of("status", "COMPLETED"))
                    .andExpect(jsonPath("$.overdue").value(false));
            call(get("/api/tasks").queryParam("overdue", "true"), manager).andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void searchMatchesTitleDescriptionAssigneeAndPart() throws Exception {
            Piece piece = newPiece("S-" + UUID.randomUUID().toString().substring(0, 6), "پروانه توربین", 4);
            Map<String, Object> body = taskBody("عنوان معمولی", worker, TaskPriority.LOW, null);
            body.put("description", "کنترل ابعاد نهایی");
            body.put("partId", piece.getId());
            call(post("/api/tasks"), manager, body).andExpect(status().isCreated());
            createTask(manager, otherWorker, "چیز دیگر", TaskPriority.LOW, null);

            for (String term : List.of("معمولی", "ابعاد", "توربین", worker.getFullName(), "S-")) {
                call(get("/api/tasks").queryParam("q", term), manager)
                        .andExpect(jsonPath("$.totalElements").value(1));
            }
            call(get("/api/tasks").queryParam("q", "نامفهوم-xyz"), manager).andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void searchTreatsWildcardCharactersLiterally() throws Exception {
            createTask(manager, worker, "پیشرفت 10% کار", TaskPriority.LOW, null);
            createTask(manager, worker, "کار عادی", TaskPriority.LOW, null);
            call(get("/api/tasks").queryParam("q", "%"), manager).andExpect(jsonPath("$.totalElements").value(1));
            call(get("/api/tasks").queryParam("q", "ک_ر"), manager).andExpect(jsonPath("$.totalElements").value(0));
            call(get("/api/tasks").queryParam("q", "' OR 1=1 --"), manager).andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void defaultSmartSortPutsOverdueFirstThenCriticalThenDueSoonAndClosedLast() throws Exception {
            String overdue = createTask(manager, worker, "t-overdue", TaskPriority.LOW, dueIn(2));
            createTask(manager, worker, "t-critical", TaskPriority.CRITICAL, dueIn(30));
            createTask(manager, worker, "t-high", TaskPriority.HIGH, dueIn(30));
            createTask(manager, worker, "t-soon", TaskPriority.LOW, dueIn(1));
            createTask(manager, worker, "t-later", TaskPriority.LOW, dueIn(9));
            createTask(manager, worker, "t-nodue", TaskPriority.LOW, null);
            String done = createTask(manager, worker, "t-done", TaskPriority.CRITICAL, dueIn(1));

            Task task = reloadTask(overdue);
            task.setDueDate(LocalDateTime.now().minusDays(1));
            taskRepository.saveAndFlush(task);
            call(patch("/api/tasks/" + done + "/status"), worker, Map.of("status", "COMPLETED")).andExpect(status().isOk());

            String body = call(get("/api/tasks").queryParam("q", "t-"), manager).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertEquals(
                    List.of("t-overdue", "t-critical", "t-high", "t-soon", "t-later", "t-nodue", "t-done"),
                    JsonPath.<List<String>>read(body, "$.items[*].title"));
        }

        @Test
        void explicitSortOverridesDefault() throws Exception {
            createTask(manager, worker, "s-low", TaskPriority.LOW, dueIn(1));
            createTask(manager, worker, "s-crit", TaskPriority.CRITICAL, dueIn(5));
            createTask(manager, worker, "s-med", TaskPriority.MEDIUM, dueIn(3));

            assertEquals(List.of("s-crit", "s-med", "s-low"), JsonPath.<List<String>>read(
                    call(get("/api/tasks").queryParam("q", "s-").queryParam("sort", "priority"), manager)
                            .andReturn().getResponse().getContentAsString(), "$.items[*].title"));
            assertEquals(List.of("s-low", "s-med", "s-crit"), JsonPath.<List<String>>read(
                    call(get("/api/tasks").queryParam("q", "s-").queryParam("sort", "dueDate"), manager)
                            .andReturn().getResponse().getContentAsString(), "$.items[*].title"));
            assertEquals(List.of("s-crit", "s-med", "s-low"), JsonPath.<List<String>>read(
                    call(get("/api/tasks").queryParam("q", "s-").queryParam("sort", "dueDate").queryParam("dir", "desc"), manager)
                            .andReturn().getResponse().getContentAsString(), "$.items[*].title"));
            // createdAt/updatedAt هم کلید معتبرند (ترتیب دقیقِ ساخت در یک میلی‌ثانیه قابل تضمین نیست؛ فقط 200 بودن)
            call(get("/api/tasks").queryParam("sort", "createdAt").queryParam("dir", "asc"), manager)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
            call(get("/api/tasks").queryParam("sort", "updatedAt"), manager)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        }

        @Test
        void listItemsAreLightweightSummaries() throws Exception {
            createTask(manager, worker, "کار", TaskPriority.LOW, null);
            String body = call(get("/api/tasks"), manager).andReturn().getResponse().getContentAsString();
            assertFalse(body.contains("\"description\""), "لیست نباید توضیحات کامل را برگرداند");
            assertFalse(body.contains("\"password\""));
            assertFalse(body.contains("\"permissions\""));
        }
    }

    // =====================================================================
    // پروفایل و آمار
    // =====================================================================
    @Nested
    @DisplayName("Profile & stats")
    class Profile {

        @Test
        void userSeesOwnProfileWithCorrectStats() throws Exception {
            String open1 = createTask(manager, worker, "باز ۱", TaskPriority.LOW, dueIn(2));
            createTask(manager, worker, "باز ۲", TaskPriority.LOW, null);
            String done = createTask(manager, worker, "انجام", TaskPriority.LOW, null);
            String cancelled = createTask(manager, worker, "لغو", TaskPriority.LOW, null);
            call(patch("/api/tasks/" + done + "/status"), worker, Map.of("status", "COMPLETED")).andExpect(status().isOk());
            call(delete("/api/tasks/" + cancelled), manager).andExpect(status().isOk());

            Task late = reloadTask(open1);
            late.setDueDate(LocalDateTime.now().minusHours(3));
            taskRepository.saveAndFlush(late);

            call(get("/api/users/" + worker.getId() + "/profile"), worker).andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(worker.getUsername()))
                    .andExpect(jsonPath("$.stats.openTasks").value(2))
                    .andExpect(jsonPath("$.stats.overdue").value(1))
                    .andExpect(jsonPath("$.stats.completed").value(1))
                    // 1 تکمیل از (1 تکمیل + 2 باز)؛ لغوشده حساب نمی‌شود
                    .andExpect(jsonPath("$.stats.completionRate").value(33))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.permissions").doesNotExist());
        }

        @Test
        void completionRateIsNullWhenUserHasNoTasks() throws Exception {
            call(get("/api/users/" + otherWorker.getId() + "/profile"), otherWorker).andExpect(status().isOk())
                    .andExpect(jsonPath("$.stats.completionRate").doesNotExist())
                    .andExpect(jsonPath("$.stats.openTasks").value(0));
        }

        @Test
        void profileOfOthersIsHiddenFromRegularUsers() throws Exception {
            call(get("/api/users/" + otherWorker.getId() + "/profile"), worker).andExpect(status().isNotFound());
            call(get("/api/users/" + UUID.randomUUID() + "/profile"), worker).andExpect(status().isNotFound());
            call(get("/api/users/" + otherWorker.getId() + "/tasks"), worker).andExpect(status().isNotFound());

            call(get("/api/users/" + worker.getId() + "/profile"), manager).andExpect(status().isOk());
            call(get("/api/users/" + worker.getId() + "/profile"), admin).andExpect(status().isOk());
        }

        @Test
        void managerSeesOnlyTasksTheyCanSeeOnAProfile() throws Exception {
            createTask(manager, worker, "از مدیر ۱", TaskPriority.LOW, null);
            createTask(otherManager, worker, "از مدیر ۲", TaskPriority.LOW, null);

            call(get("/api/users/" + worker.getId() + "/tasks"), manager)
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.items[0].title").value("از مدیر ۱"));
            call(get("/api/users/" + worker.getId() + "/tasks"), admin).andExpect(jsonPath("$.totalElements").value(2));
            call(get("/api/users/" + worker.getId() + "/tasks"), worker).andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void onlyAdminCanEditProfileAndCannotDisableSelf() throws Exception {
            call(patch("/api/users/" + worker.getId() + "/profile"), manager, Map.of("department", "x"))
                    .andExpect(status().isForbidden());
            call(patch("/api/users/" + worker.getId() + "/profile"), admin, Map.of("department", "  ریخته‌گری  "))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.department").value("ریخته‌گری"));
            call(patch("/api/users/" + admin.getId() + "/profile"), admin, Map.of("active", false))
                    .andExpect(status().is4xxClientError());

            call(patch("/api/users/" + worker.getId() + "/profile"), admin, Map.of("active", false))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false))
                    .andExpect(jsonPath("$.canReceiveTasks").value(false));
            call(post("/api/tasks"), manager, taskBody("x", worker, TaskPriority.LOW, null))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void userWithTasksCannotBeDeleted() throws Exception {
            createTask(manager, worker, "کار", TaskPriority.LOW, null);
            call(delete("/api/users/" + worker.getId()), admin).andExpect(status().isConflict());
            assertTrue(userRepository.existsById(worker.getId()));
        }
    }
}
