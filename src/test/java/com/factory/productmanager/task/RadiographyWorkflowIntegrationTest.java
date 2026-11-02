package com.factory.productmanager.task;

import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.RadiographyWorkflowRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * گردش‌کار تفسیر رادیوگرافی از ابتدا تا انتها، روی برنامه‌ی کامل (Security + JPA) با H2:
 * تخصیص → وظیفه‌ی مفسر → صدور و تأیید مفسر → وظیفه‌ی مدیر → تأیید/رد مدیر → انتقال قطعه به VT.
 */
class RadiographyWorkflowIntegrationTest extends TaskTestBase {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired RadiographyWorkflowRepository workflowRepository;

    User reviewer;
    User interpreter;
    User otherInterpreter;
    User editorOnly;
    Piece piece;
    UUID templateLineId;
    UUID templateId;

    @BeforeEach
    void seedWorkflowData() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);
        reviewer = newUser("t_rev_" + s, Role.OPERATOR,
                Set.of(Permission.REVIEW_RADIOGRAPHY_REPORT, Permission.MANAGE_TASKS, Permission.RECEIVE_TASKS));
        interpreter = newUser("t_int_" + s, Role.OPERATOR,
                Set.of(Permission.INTERPRET_RADIOGRAPHY, Permission.MANAGE_RADIOGRAPHY_DEFECTS, Permission.RECEIVE_TASKS));
        otherInterpreter = newUser("t_int2_" + s, Role.OPERATOR,
                Set.of(Permission.INTERPRET_RADIOGRAPHY, Permission.MANAGE_RADIOGRAPHY_DEFECTS, Permission.RECEIVE_TASKS));
        editorOnly = newUser("t_edit_" + s, Role.OPERATOR, Set.of(Permission.MANAGE_RADIOGRAPHY_DEFECTS));

        piece = newPiece("RW" + s, "Casting-" + s, 1);
        piece.setCurrentStage(PieceStage.RADIOGRAPHY);
        piece.setRadiographySubStage(RadiographySubStage.INTERPRETATION);
        piece = pieceRepository.saveAndFlush(piece);

        String body = call(post("/api/piece-definitions/" + piece.getPieceDefinition().getId() + "/radiography-templates"),
                admin, Map.of("name", "Tpl", "lines", List.of(Map.of("courseCode", "C1"))))
                .andReturn().getResponse().getContentAsString();
        templateId = UUID.fromString(JsonPath.read(body, "$.id"));
        templateLineId = UUID.fromString(JsonPath.read(body, "$.lines[0].id"));
    }

    // ------------------------------------------------------------------ helpers

    private String assign(User by, User who) throws Exception {
        String body = call(post("/api/radiography/pieces/" + piece.getId() + "/workflow/assign"), by,
                Map.of("interpreterId", who.getId()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions saveReport(User as, String result) throws Exception {
        Map<String, Object> line = new java.util.LinkedHashMap<>();
        line.put("templateLineId", templateLineId);
        line.put("iqiReal", "10");
        line.put("result", result);
        return call(put("/api/pieces/" + piece.getId() + "/radiography-reports"), as,
                Map.of("templateId", templateId, "lines", List.of(line)));
    }

    private void uploadSignature(User as) throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/profile/signature")
                        .file(new MockMultipartFile("file", "sig.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + token(as)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(true));
    }

    private ResultActions act(String url, User as, Object body) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url), as, body);
    }

    private String issue(String workflowId) throws Exception {
        saveReport(interpreter, "RS").andExpect(status().isOk());
        uploadSignature(interpreter);
        return act("/api/radiography/workflows/" + workflowId + "/interpreter-approve", interpreter,
                Map.of("confirmed", true))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Piece reloadPiece() {
        pieceRepository.flush();
        return pieceRepository.findById(piece.getId()).orElseThrow();
    }

    private Task task(String id) {
        return reloadTask(id);
    }

    // ------------------------------------------------------------------ happy path

    @Test
    void fullWorkflow_assign_interpret_approve_moves_piece_to_vt() throws Exception {
        String wf = assign(reviewer, interpreter);

        // وظیفه‌ی مفسر: نوع، اولویت، اتصال به قطعه/پروژه، مسئول
        Task interpreterTask = workflowRepository.findById(UUID.fromString(wf)).orElseThrow().getInterpreterTask();
        assertThat(interpreterTask.getTaskType()).isEqualTo(TaskType.RADIOGRAPHY_INTERPRETATION);
        assertThat(interpreterTask.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(interpreterTask.getPart().getId()).isEqualTo(piece.getId());
        assertThat(interpreterTask.getProject().getId()).isEqualTo(piece.getPieceDefinition().getProject().getId());
        assertThat(interpreterTask.getAssignedTo().getId()).isEqualTo(interpreter.getId());
        assertThat(interpreterTask.getWorkflowId()).isEqualTo(UUID.fromString(wf));

        // مفسر وظیفه را در «وظایف من» می‌بیند و نوع آن در فهرست می‌آید
        call(get("/api/tasks?mine=true"), interpreter).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + interpreterTask.getId() + "')].taskType")
                        .value(hasItem("RADIOGRAPHY_INTERPRETATION")));
        // اعلان به مفسر
        assertThat(notificationRepository.findAll().stream()
                .anyMatch(n -> n.getType() == NotificationType.RADIOGRAPHY_ASSIGNED
                        && n.getRecipient().getId().equals(interpreter.getId()))).isTrue();

        // اولین ویرایش مفسر گردش‌کار را به «در حال تفسیر» می‌برد
        saveReport(interpreter, "RS").andExpect(status().isOk());
        String reports = call(get("/api/pieces/" + piece.getId() + "/radiography-reports"), interpreter)
                .andReturn().getResponse().getContentAsString();
        String reshoot = call(post("/api/pieces/" + piece.getId() + "/radiography-defects"), interpreter,
                Map.of("reportLineId", (String) JsonPath.read(reports, "$[0].lines[0].id"),
                        "defectType", "F.M", "welded", true, "reshootOk", true,
                        "positions", List.of(Map.of("startCm", "L4"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        call(get("/api/radiography/workflows/" + wf), interpreter)
                .andExpect(jsonPath("$.status").value("REPORT_IN_PROGRESS"));
        assertThat(task(interpreterTask.getId().toString()).getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        // بدون امضا در پروفایل، تأیید ممکن نیست
        act("/api/radiography/workflows/" + wf + "/interpreter-approve", interpreter, Map.of("confirmed", true))
                .andExpect(status().isBadRequest());
        uploadSignature(interpreter);

        // بدون تأیید صریح کاربر هم ثبت نمی‌شود
        act("/api/radiography/workflows/" + wf + "/interpreter-approve", interpreter, Map.of("confirmed", false))
                .andExpect(status().isBadRequest());

        String afterInterpreter = act("/api/radiography/workflows/" + wf + "/interpreter-approve", interpreter,
                Map.of("confirmed", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MANAGER_REVIEW"))
                .andExpect(jsonPath("$.interpreterApproval.approverUsername").value(interpreter.getUsername()))
                .andExpect(jsonPath("$.interpreterApproval.hasSignature").value(true))
                .andReturn().getResponse().getContentAsString();
        String managerTaskId = JsonPath.read(afterInterpreter, "$.managerTaskId");

        assertThat(task(interpreterTask.getId().toString()).getStatus()).isEqualTo(TaskStatus.COMPLETED);
        Task managerTask = task(managerTaskId);
        assertThat(managerTask.getTaskType()).isEqualTo(TaskType.RADIOGRAPHY_MANAGER_APPROVAL);
        assertThat(managerTask.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(managerTask.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(managerTask.getAssignedTo().getId()).isEqualTo(reviewer.getId());
        assertThat(managerTask.getPart().getId()).isEqualTo(piece.getId());

        // مرحله‌ی قطعه هنوز تغییر نکرده
        assertThat(reloadPiece().getRadiographySubStage()).isEqualTo(RadiographySubStage.INTERPRETATION);

        // مدیر بدون امضا نمی‌تواند تأیید کند؛ بعد از ثبت امضا می‌تواند
        act("/api/radiography/workflows/" + wf + "/manager-approve", reviewer, Map.of("confirmed", true))
                .andExpect(status().isBadRequest());
        uploadSignature(reviewer);
        act("/api/radiography/workflows/" + wf + "/manager-approve", reviewer, Map.of("confirmed", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MANAGER_APPROVED"))
                .andExpect(jsonPath("$.open").value(false))
                .andExpect(jsonPath("$.managerApproval.approverUsername").value(reviewer.getUsername()))
                .andExpect(jsonPath("$.versions.length()").value(3))
                .andExpect(jsonPath("$.versions[0].kind").value("ORIGINAL"))
                .andExpect(jsonPath("$.versions[1].kind").value("INTERPRETER_APPROVED"))
                .andExpect(jsonPath("$.versions[2].kind").value("MANAGER_FINAL"))
                .andExpect(jsonPath("$.events[*].action").value(hasItem("PART_STAGE_CHANGED")))
                .andExpect(jsonPath("$.events[*].action").value(hasItem("RADIOGRAPHY_MANAGER_APPROVED")))
                .andExpect(jsonPath("$.events[*].action").value(hasItem("RADIOGRAPHY_INTERPRETER_APPROVED")))
                .andExpect(jsonPath("$.events[*].action").value(hasItem("RADIOGRAPHY_REPORT_ISSUED")));

        // مرحله‌ی قطعه فقط در بک‌اند عوض شده: VT
        Piece moved = reloadPiece();
        assertThat(moved.getCurrentStage()).isEqualTo(PieceStage.RADIOGRAPHY);
        assertThat(moved.getRadiographySubStage()).isEqualTo(RadiographySubStage.VT);
        // وظیفه‌ی مدیر تکمیل شد (حذف نشد)
        Task finished = task(managerTaskId);
        assertThat(finished.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(finished.getCompletedAt()).isNotNull();
        // گزارش نهایی شد
        call(get("/api/pieces/" + piece.getId() + "/radiography-reports"), admin)
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
        // اعلان به مفسر
        assertThat(notificationRepository.findAll().stream()
                .anyMatch(n -> n.getType() == NotificationType.RADIOGRAPHY_APPROVED
                        && n.getRecipient().getId().equals(interpreter.getId()))).isTrue();

        // سند نهایی: مهر و امضای مفسر و مدیر (کاربر عادی هم می‌بیند)
        List<String> versionIds = JsonPath.read(
                call(get("/api/radiography/workflows/" + wf), reviewer).andReturn().getResponse().getContentAsString(),
                "$.versions[*].id");
        call(get("/api/radiography/workflows/" + wf + "/versions/" + versionIds.get(2) + "/document"), outsider)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].lines[0].defects[0].reshootOk").value(true))
                .andExpect(jsonPath("$.reports[0].lines[0].defects[0].positions[0].startCm").value("L4"))
                .andExpect(jsonPath("$.reports[0].lines[0].defects[0].chainText").value("(L4) F.M → ریشوت → جوش → OK"))
                .andExpect(jsonPath("$.approvals.length()").value(2))
                .andExpect(jsonPath("$.approvals[0].type").value("INTERPRETER"))
                .andExpect(jsonPath("$.approvals[1].type").value("MANAGER"))
                .andExpect(jsonPath("$.approvals[0].signatureDataUrl").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.reports[0].lines[0].result").value("RS"));
        // نسخه‌ی میانی فقط برای دست‌اندرکاران
        call(get("/api/radiography/workflows/" + wf + "/versions/" + versionIds.get(1) + "/document"), outsider)
                .andExpect(status().isNotFound());

        // گردش‌کار بسته شد: دوباره قابل تخصیص نیست چون قطعه دیگر در تفسیر نیست
        act("/api/radiography/pieces/" + piece.getId() + "/workflow/assign", reviewer,
                Map.of("interpreterId", interpreter.getId())).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ reject loop

    @Test
    void reject_requires_reason_reopens_interpreter_task_and_allows_second_round() throws Exception {
        String wf = assign(reviewer, interpreter);
        String first = issue(wf);
        String firstManagerTask = JsonPath.read(first, "$.managerTaskId");
        String interpreterTaskId = JsonPath.read(first, "$.interpreterTaskId");

        act("/api/radiography/workflows/" + wf + "/manager-reject", reviewer, Map.of("reason", "  "))
                .andExpect(status().isBadRequest());
        act("/api/radiography/workflows/" + wf + "/manager-reject", reviewer, Map.of("reason", "IQI نامعتبر است"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REINTERPRETATION_REQUIRED"))
                .andExpect(jsonPath("$.rejectionReason").value("IQI نامعتبر است"))
                .andExpect(jsonPath("$.round").value(2));

        // وظیفه‌ی مدیر بسته؛ وظیفه‌ی مفسر دوباره باز؛ قطعه همچنان در تفسیر
        assertThat(task(firstManagerTask).getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task(interpreterTaskId).getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(reloadPiece().getRadiographySubStage()).isEqualTo(RadiographySubStage.INTERPRETATION);
        call(get("/api/pieces/" + piece.getId() + "/radiography-reports"), admin)
                .andExpect(jsonPath("$[0].status").value("REINTERPRETATION_REQUIRED"));
        // مفسر دلیل رد را در اعلان می‌گیرد
        assertThat(notificationRepository.findAll().stream()
                .anyMatch(n -> n.getType() == NotificationType.RADIOGRAPHY_REJECTED
                        && n.getRecipient().getId().equals(interpreter.getId())
                        && n.getMessage().contains("IQI نامعتبر است"))).isTrue();

        // دور دوم: ویرایش دوباره‌ی مفسر مجاز است و تأیید دوباره وظیفه‌ی مدیر جدید می‌سازد
        saveReport(interpreter, "RT").andExpect(status().isOk());
        String second = act("/api/radiography/workflows/" + wf + "/interpreter-approve", interpreter,
                Map.of("confirmed", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MANAGER_REVIEW"))
                .andReturn().getResponse().getContentAsString();
        String secondManagerTask = JsonPath.read(second, "$.managerTaskId");
        assertThat(secondManagerTask).isNotEqualTo(firstManagerTask);

        uploadSignature(reviewer);
        act("/api/radiography/workflows/" + wf + "/manager-approve", reviewer, Map.of("confirmed", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions.length()").value(4));
        assertThat(reloadPiece().getRadiographySubStage()).isEqualTo(RadiographySubStage.VT);
    }

    // ------------------------------------------------------------------ guards

    @Test
    void duplicate_assignment_and_invalid_targets_are_rejected() throws Exception {
        assign(reviewer, interpreter);
        act("/api/radiography/pieces/" + piece.getId() + "/workflow/assign", reviewer,
                Map.of("interpreterId", otherInterpreter.getId())).andExpect(status().isBadRequest());
        assertThat(taskRepository.findAll().stream()
                .filter(t -> t.getTaskType() == TaskType.RADIOGRAPHY_INTERPRETATION).count()).isEqualTo(1);

        // قطعه‌ای که در تفسیر نیست
        Piece other = newPiece("RX" + UUID.randomUUID().toString().substring(0, 6), "Other", 2);
        act("/api/radiography/pieces/" + other.getId() + "/workflow/assign", reviewer,
                Map.of("interpreterId", interpreter.getId())).andExpect(status().isBadRequest());
    }

    @Test
    void ineligible_interpreter_gets_uniform_error() throws Exception {
        act("/api/radiography/pieces/" + piece.getId() + "/workflow/assign", reviewer,
                Map.of("interpreterId", worker.getId())).andExpect(status().isBadRequest());
        act("/api/radiography/pieces/" + piece.getId() + "/workflow/assign", reviewer,
                Map.of("interpreterId", UUID.randomUUID())).andExpect(status().isBadRequest());
        interpreter.setDisabled(true);
        userRepository.saveAndFlush(interpreter);
        act("/api/radiography/pieces/" + piece.getId() + "/workflow/assign", reviewer,
                Map.of("interpreterId", interpreter.getId())).andExpect(status().isBadRequest());
    }

    @Test
    void only_authorized_users_can_act_on_the_workflow() throws Exception {
        // کاربر بدون دسترسی نمی‌تواند تخصیص دهد
        act("/api/radiography/pieces/" + piece.getId() + "/workflow/assign", interpreter,
                Map.of("interpreterId", interpreter.getId())).andExpect(status().isForbidden());

        String wf = assign(reviewer, interpreter);
        saveReport(interpreter, "RS").andExpect(status().isOk());
        uploadSignature(interpreter);

        // مفسر دیگر: وجود گردش‌کار را نمی‌فهمد (۴۰۴)
        act("/api/radiography/workflows/" + wf + "/interpreter-approve", otherInterpreter, Map.of("confirmed", true))
                .andExpect(status().isNotFound());
        call(get("/api/radiography/workflows/" + wf), otherInterpreter).andExpect(status().isNotFound());
        call(get("/api/radiography/workflows/" + wf), outsider).andExpect(status().isNotFound());
        // مدیر هم نمی‌تواند به‌جای مفسر امضا کند
        act("/api/radiography/workflows/" + wf + "/interpreter-approve", reviewer, Map.of("confirmed", true))
                .andExpect(status().isForbidden());
        // مفسر نمی‌تواند تصمیم مدیر را بگیرد
        act("/api/radiography/workflows/" + wf + "/manager-approve", interpreter, Map.of("confirmed", true))
                .andExpect(status().isForbidden());
        // تأیید مدیر پیش از تأیید مفسر ممکن نیست
        act("/api/radiography/workflows/" + wf + "/manager-approve", reviewer, Map.of("confirmed", true))
                .andExpect(status().isBadRequest());
        // شناسه‌ی نامعتبر
        act("/api/radiography/workflows/" + UUID.randomUUID() + "/manager-approve", reviewer, Map.of("confirmed", true))
                .andExpect(status().isNotFound());
        // ادمین گردش‌کار را می‌بیند
        call(get("/api/radiography/workflows/" + wf), admin).andExpect(status().isOk());
    }

    @Test
    void report_editing_is_restricted_and_locked_during_workflow() throws Exception {
        String wf = assign(reviewer, interpreter);

        // فقط مفسر مسئول (یا ادمین) در طول تفسیر گزارش را ویرایش می‌کند
        saveReport(editorOnly, "RS").andExpect(status().isForbidden());
        saveReport(otherInterpreter, "RS").andExpect(status().isForbidden());
        saveReport(interpreter, "RS").andExpect(status().isOk());

        // مرحله‌ی قطعه را نمی‌شود دستی عوض کرد
        call(post("/api/pieces/" + piece.getId() + "/advance"), admin, Map.of())
                .andExpect(status().isBadRequest());
        call(patch("/api/pieces/" + piece.getId() + "/radiography-substage"), admin,
                Map.of("radiographySubStage", "VT")).andExpect(status().isBadRequest());

        // وظیفه‌ی گردش‌کار با API عمومی وظایف تغییر نمی‌کند
        UUID taskId = workflowRepository.findById(UUID.fromString(wf)).orElseThrow().getInterpreterTask().getId();
        call(patch("/api/tasks/" + taskId + "/status"), interpreter, Map.of("status", "COMPLETED"))
                .andExpect(status().isBadRequest());
        call(patch("/api/tasks/" + taskId + "/assignee"), admin, Map.of("assigneeId", otherInterpreter.getId()))
                .andExpect(status().isBadRequest());
        call(delete("/api/tasks/" + taskId), admin).andExpect(status().isBadRequest());

        // پس از تأیید مفسر، گزارش برای غیرمدیر قفل است
        uploadSignature(interpreter);
        act("/api/radiography/workflows/" + wf + "/interpreter-approve", interpreter, Map.of("confirmed", true))
                .andExpect(status().isOk());
        saveReport(interpreter, "RT").andExpect(status().isBadRequest());
        saveReport(admin, "RT").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_MANAGER_APPROVAL"));
        // و تأیید دوباره‌ی مفسر (دوبار کلیک) رد می‌شود
        act("/api/radiography/workflows/" + wf + "/interpreter-approve", interpreter, Map.of("confirmed", true))
                .andExpect(status().isBadRequest());
    }

    @Test
    void double_manager_approval_is_rejected() throws Exception {
        String wf = assign(reviewer, interpreter);
        issue(wf);
        uploadSignature(reviewer);
        act("/api/radiography/workflows/" + wf + "/manager-approve", reviewer, Map.of("confirmed", true))
                .andExpect(status().isOk());
        act("/api/radiography/workflows/" + wf + "/manager-approve", reviewer, Map.of("confirmed", true))
                .andExpect(status().isBadRequest());
        act("/api/radiography/workflows/" + wf + "/manager-reject", reviewer, Map.of("reason", "x"))
                .andExpect(status().isBadRequest());
        assertThat(workflowRepository.findById(UUID.fromString(wf)).orElseThrow().getOpenKey()).isNull();
    }

    @Test
    void incomplete_report_cannot_be_issued() throws Exception {
        String wf = assign(reviewer, interpreter);
        uploadSignature(interpreter);
        // هیچ گزارشی ثبت نشده
        act("/api/radiography/workflows/" + wf + "/interpreter-approve", interpreter, Map.of("confirmed", true))
                .andExpect(status().isBadRequest());
        // نتیجه‌ی کورس خالی
        saveReport(interpreter, null).andExpect(status().isOk());
        act("/api/radiography/workflows/" + wf + "/interpreter-approve", interpreter, Map.of("confirmed", true))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancel_and_reassign_keep_workflow_consistent() throws Exception {
        String wf = assign(reviewer, interpreter);
        act("/api/radiography/workflows/" + wf + "/reassign", reviewer, Map.of("interpreterId", otherInterpreter.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interpreter.id").value(otherInterpreter.getId().toString()));
        UUID taskId = workflowRepository.findById(UUID.fromString(wf)).orElseThrow().getInterpreterTask().getId();
        assertThat(task(taskId.toString()).getAssignedTo().getId()).isEqualTo(otherInterpreter.getId());
        // مفسر قبلی دیگر اجازه‌ی ویرایش ندارد
        saveReport(interpreter, "RS").andExpect(status().isForbidden());

        act("/api/radiography/workflows/" + wf + "/cancel", reviewer, Map.of("reason", " ")).andExpect(status().isBadRequest());
        act("/api/radiography/workflows/" + wf + "/cancel", reviewer, Map.of("reason", "اشتباه تخصیص"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(task(taskId.toString()).getStatus()).isEqualTo(TaskStatus.CANCELLED);
        // قطعه آزاد شد: دوباره تخصیص‌پذیر است
        assign(reviewer, interpreter);
        // و ویرایش دستی مرحله هم دوباره ممکن است وقتی گردش‌کار باز نیست (در اینجا گردش‌کار جدید باز است)
    }

    @Test
    void queue_and_piece_state_reflect_workflow() throws Exception {
        call(get("/api/radiography/queue"), reviewer).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].pieceId").value(hasItem(piece.getId().toString())));
        call(get("/api/radiography/queue"), interpreter).andExpect(status().isForbidden());
        call(get("/api/radiography/pieces/" + piece.getId() + "/workflow"), reviewer)
                .andExpect(jsonPath("$.canAssign").value(true))
                .andExpect(jsonPath("$.inInterpretation").value(true));
        call(get("/api/radiography/pieces/" + piece.getId() + "/workflow"), interpreter)
                .andExpect(jsonPath("$.canAssign").value(false));

        assign(reviewer, interpreter);
        call(get("/api/radiography/pieces/" + piece.getId() + "/workflow"), reviewer)
                .andExpect(jsonPath("$.canAssign").value(false))
                .andExpect(jsonPath("$.workflow.status").value("INTERPRETER_ASSIGNED"))
                .andExpect(jsonPath("$.workflow.steps[0].state").value("CURRENT"));
        call(get("/api/radiography/interpreters"), reviewer).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username").value(hasItem(interpreter.getUsername())));
    }

    @Test
    void signature_upload_validates_content_and_is_private_to_owner() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/profile/signature")
                        .file(new MockMultipartFile("file", "sig.png", "image/png", "not an image".getBytes()))
                        .header("Authorization", "Bearer " + token(interpreter)))
                .andExpect(status().isBadRequest());
        call(get("/api/profile/signature"), interpreter).andExpect(jsonPath("$.present").value(false));
        uploadSignature(interpreter);
        call(get("/api/profile/signature"), interpreter)
                .andExpect(jsonPath("$.present").value(true))
                .andExpect(jsonPath("$.dataUrl").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));
        // امضای دیگران دیده نمی‌شود: مسیر فقط کاربر جاری را برمی‌گرداند
        call(get("/api/profile/signature"), otherInterpreter).andExpect(jsonPath("$.present").value(false));
        call(delete("/api/profile/signature"), interpreter).andExpect(status().isNoContent());
        call(get("/api/profile/signature"), interpreter).andExpect(jsonPath("$.present").value(false));
    }
    private Map<String,Object> defectBody() {
        return Map.of("course","C1","defectType","CR","status","NEEDS_WELDING");
    }
    private String createStandaloneDefect(User as) throws Exception {
        return JsonPath.read(call(post("/api/pieces/"+piece.getId()+"/radiography-defects"),as,defectBody())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),"$.id");
    }
    @Test void allPendingReportAndDefectMutationsRejectNonManagersIncludingAdmin() throws Exception {
        String wf=assign(reviewer,interpreter);
        String defect=createStandaloneDefect(interpreter);
        issue(wf);
        User limitedAdmin=newUser("rt-admin-"+UUID.randomUUID(),Role.ADMIN,Set.of(Permission.MANAGE_RADIOGRAPHY_DEFECTS));
        String reportId=JsonPath.read(call(get("/api/pieces/"+piece.getId()+"/radiography-reports"),admin)
                .andReturn().getResponse().getContentAsString(),"$[0].id");
        for(User as:List.of(interpreter,editorOnly,limitedAdmin)) {
            saveReport(as,"RT").andExpect(status().isBadRequest());
            call(post("/api/pieces/"+piece.getId()+"/radiography-defects"),as,defectBody()).andExpect(status().isBadRequest());
            call(patch("/api/radiography-defects/"+defect),as,defectBody()).andExpect(status().isBadRequest());
            call(delete("/api/radiography-defects/"+defect),as).andExpect(status().isBadRequest());
            call(patch("/api/radiography-defects/"+defect+"/confirm-welding"),as).andExpect(status().isBadRequest());
            call(delete("/api/radiography-reports/"+reportId),as).andExpect(status().isBadRequest());
        }
        call(get("/api/pieces/"+piece.getId()+"/radiography-defects"),admin)
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].weldingConfirmed").value(false));
        call(get("/api/radiography/workflows/"+wf),reviewer).andExpect(jsonPath("$.status").value("MANAGER_REVIEW"));
    }
    @Test void fullManagerMayEditPendingReportAndDefectsWithoutReopeningWorkflow() throws Exception {
        String wf=assign(reviewer,interpreter);String defect=createStandaloneDefect(interpreter);issue(wf);
        saveReport(admin,"RT").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_MANAGER_APPROVAL"));
        call(patch("/api/radiography-defects/"+defect),admin,defectBody()).andExpect(status().isOk());
        call(patch("/api/radiography-defects/"+defect+"/confirm-welding"),admin).andExpect(status().isOk());
        call(delete("/api/radiography-defects/"+defect),admin).andExpect(status().isNoContent());
        createStandaloneDefect(admin);
        call(get("/api/radiography/workflows/"+wf),admin).andExpect(jsonPath("$.status").value("MANAGER_REVIEW"))
                .andExpect(jsonPath("$.permissions.canEditReport").value(true));
        call(get("/api/radiography/workflows/"+wf),interpreter).andExpect(jsonPath("$.permissions.canEditReport").value(false));
    }
    @Test void templateSideDoorAndReadSynchronizationCannotAlterPendingReportForNonManager() throws Exception {
        String wf=assign(reviewer,interpreter);issue(wf);
        User designer=newUser("rt-designer-"+UUID.randomUUID(),Role.ADMIN,Set.of(Permission.MANAGE_PIECE_DEFINITIONS));
        var template=Map.of("name","Tpl","lines",List.of(Map.of("id",templateLineId,"courseCode","C1"),Map.of("courseCode","C2")));
        call(put("/api/radiography-templates/"+templateId),designer,template).andExpect(status().isBadRequest());
        call(post("/api/piece-definitions/"+piece.getPieceDefinition().getId()+"/radiography-templates"),designer,
                Map.of("name","Another","lines",List.of(Map.of("courseCode","C3")))).andExpect(status().isBadRequest());
        call(put("/api/radiography-templates/"+templateId),admin,template).andExpect(status().isOk());
        call(get("/api/pieces/"+piece.getId()+"/radiography-reports"),interpreter).andExpect(jsonPath("$[0].lines.length()").value(1));
        saveReport(admin,"RT").andExpect(status().isOk()).andExpect(jsonPath("$.lines.length()").value(2));
    }
    @Test void reportAndDefectEditingReopensForAssignedInterpreterAfterRejection() throws Exception {
        String wf=assign(reviewer,interpreter);issue(wf);
        act("/api/radiography/workflows/"+wf+"/manager-reject",reviewer,Map.of("reason","اصلاح لازم است"))
                .andExpect(status().isOk());
        saveReport(interpreter,"RT").andExpect(status().isOk());createStandaloneDefect(interpreter);
        saveReport(editorOnly,"RT").andExpect(status().isForbidden());
    }
    private void rejectSamePersonFinalApproval(Role role) throws Exception {
        interpreter.setRole(role);
        interpreter.getPermissions().add(Permission.REVIEW_RADIOGRAPHY_REPORT);
        userRepository.saveAndFlush(interpreter);
        String wf=assign(interpreter,interpreter);issue(wf);
        call(get("/api/radiography/workflows/"+wf),interpreter)
                .andExpect(jsonPath("$.permissions.canReview").value(true))
                .andExpect(jsonPath("$.permissions.canApprove").value(false));
        act("/api/radiography/workflows/"+wf+"/manager-approve",interpreter,Map.of("confirmed",true)).andExpect(status().isForbidden());
        call(get("/api/radiography/workflows/"+wf),admin).andExpect(jsonPath("$.approvals.length()").value(1));
        var assigned=workflowRepository.findById(UUID.fromString(wf)).orElseThrow().getManagerTask().getAssignedTo();
        assertThat(assigned.getId()).isNotEqualTo(interpreter.getId());
        assertThat(reloadPiece().getRadiographySubStage()).isEqualTo(RadiographySubStage.INTERPRETATION);
    }
    @Test void interpreterWithReviewGrantCannotAlsoApproveOwnReport() throws Exception { rejectSamePersonFinalApproval(Role.OPERATOR); }
    @Test void adminWithBothGrantsIsNotExemptFromTwoPersonApproval() throws Exception { rejectSamePersonFinalApproval(Role.ADMIN); }
    @Test void fullManagerMaySignAsInterpreterAndFinalApproverUsingOwnIdentity() throws Exception {
        String wf=assign(admin,admin);saveReport(admin,"RS").andExpect(status().isOk());uploadSignature(admin);
        act("/api/radiography/workflows/"+wf+"/interpreter-approve",admin,Map.of("confirmed",true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.canApprove").value(true));
        act("/api/radiography/workflows/"+wf+"/manager-approve",admin,Map.of("confirmed",true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.interpreterApproval.approverUsername").value(admin.getUsername()))
                .andExpect(jsonPath("$.managerApproval.approverUsername").value(admin.getUsername()));
    }

}
