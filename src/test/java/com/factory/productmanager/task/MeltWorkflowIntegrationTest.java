package com.factory.productmanager.task;

import com.factory.productmanager.model.dto.*;
import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.service.MeltPassportService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MeltWorkflowIntegrationTest extends TaskTestBase {
    @Autowired MeltPassportService service;
    Piece first, second;

    @BeforeEach void fixture() {
        first = newPiece("MELT-WF-" + UUID.randomUUID(), "قطعه آزمایشی", 1);
        first.setCurrentStage(PieceStage.CASTING); pieceRepository.saveAndFlush(first);
        second = new Piece(); second.setPieceDefinition(first.getPieceDefinition()); second.setPieceNumber(2);
        second.setCurrentStage(PieceStage.CASTING); pieceRepository.saveAndFlush(second);
        // Reviewer deliberately has no dashboard, task-management or receive-task permission.
        outsider.getPermissions().add(Permission.APPROVE_MELT_PASSPORT); userRepository.saveAndFlush(outsider);
    }

    MeltPassportResponse assigned() {
        return service.assignPiece(first.getId(), new MeltAssignmentRequest(worker.getId(), null), admin.getUsername());
    }
    MeltDecisionRequest decision(MeltPassportResponse m) { return new MeltDecisionRequest(m.version(), null); }
    MeltPassportResponse fill(MeltPassportResponse m, Piece... members) {
        var r = new MeltPassportRequest(); r.setVersion(m.version()); r.setPieceIds(Arrays.stream(members).map(Piece::getId).toList());
        r.setMeltDate(LocalDate.of(2026,10,29)); r.setFurnaceNumber(BigDecimal.ONE); r.setIngotType("آزمایشی");
        r.setPrimaryWeight(BigDecimal.TEN); r.setRecycledWeight(BigDecimal.ZERO); r.setInjectionOperator("اپراتور");
        r.setChecks(Arrays.stream(MeltCheckKind.values()).map(k -> new MeltPassportRequest.CheckLine(k, MeltCheckStatus.ACCEPTED, null)).toList());
        return service.update(m.id(), r, worker.getUsername());
    }
    Task task(MeltPassportResponse m, TaskType type) {
        return taskRepository.findByWorkflowIdAndTaskType(m.id(), type).get(0);
    }

    @Test void assignedOperatorAddsUnassignedPieceAndApprovalMovesBothAndCompletesTasks() throws Exception {
        var m = assigned(); var entry = task(m, TaskType.MELT_PASSPORT_ENTRY);
        assertEquals(worker.getId(), entry.getAssignedTo().getId());
        call(get("/api/tasks").param("mine","true"), worker).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '"+entry.getId()+"')]").isNotEmpty());
        m = fill(m, first, second); m = service.submit(m.id(), decision(m), worker.getUsername());
        assertEquals(TaskStatus.COMPLETED, entry.getStatus());
        var approval = task(m, TaskType.MELT_PASSPORT_APPROVAL);
        assertTrue(taskRepository.countGroupedByStatus(worker.getId()).stream().noneMatch(row -> row[0] == TaskStatus.TODO));
        for (var reviewer : List.of(admin, outsider)) {
            call(get("/api/tasks").param("mine","true"), reviewer).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[?(@.id == '"+approval.getId()+"')]").isNotEmpty());
            call(get("/api/tasks/"+approval.getId()), reviewer).andExpect(status().isOk());
        }
        m = service.approve(m.id(), decision(m), outsider.getUsername());
        assertEquals(MeltPassportStatus.APPROVED, m.status());
        assertNotEquals(PieceStage.CASTING, first.getCurrentStage());
        assertEquals(first.getCurrentStage(), second.getCurrentStage());
        assertEquals(TaskStatus.COMPLETED, approval.getStatus());
    }

    @Test void returnReopensEntryAndReassignReusesTaskWithoutDuplicates() {
        var m = fill(assigned(), first); var entry = task(m, TaskType.MELT_PASSPORT_ENTRY);
        m = service.submit(m.id(), decision(m), worker.getUsername());
        m = service.returnForCorrection(m.id(), new MeltDecisionRequest(m.version(), "تکمیل اطلاعات"), outsider.getUsername());
        assertEquals(TaskStatus.TODO, entry.getStatus());
        m = service.assign(m.id(), new MeltAssignmentRequest(otherWorker.getId(),m.version()), admin.getUsername());
        assertEquals(otherWorker.getId(),entry.getAssignedTo().getId());
        assertEquals(1,taskRepository.findByWorkflowIdAndTaskType(m.id(),TaskType.MELT_PASSPORT_ENTRY).size());
        assertFalse(service.get(m.id(),worker.getUsername()).editable());
        assertTrue(service.get(m.id(),otherWorker.getUsername()).editable());
        m = service.submit(m.id(),decision(m),otherWorker.getUsername());
        assertEquals(1,taskRepository.findByWorkflowIdAndTaskType(m.id(),TaskType.MELT_PASSPORT_APPROVAL).stream().filter(t->t.getStatus().isOpen()).count());
    }

    @Test void approvalVisibilityFollowsLivePermissionIncludingNestedResources() throws Exception {
        var m = fill(assigned(), first); m = service.submit(m.id(),decision(m),worker.getUsername());
        var approval = task(m,TaskType.MELT_PASSPORT_APPROVAL);
        // The submitter must not gain reviewer task access from createdBy/assignedTo.
        for (var hidden : List.of(worker,otherManager,otherWorker))
            call(get("/api/tasks/"+approval.getId()),hidden).andExpect(status().isNotFound());
        call(get("/api/tasks/"+approval.getId()+"/comments"),otherWorker).andExpect(status().isNotFound());
        call(post("/api/tasks/"+approval.getId()+"/comments"),outsider,Map.of("content","یادداشت بررسی ذوب")).andExpect(status().isCreated());
        assertTrue(notificationRepository.findAll().stream().noneMatch(n -> n.getTask()!=null
                && n.getTask().getId().equals(approval.getId()) && n.getRecipient().getId().equals(worker.getId())));
        outsider.getPermissions().remove(Permission.APPROVE_MELT_PASSPORT); userRepository.saveAndFlush(outsider);
        call(get("/api/tasks/"+approval.getId()),outsider).andExpect(status().isNotFound());
        call(get("/api/tasks").param("mine","true"),outsider).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '"+approval.getId()+"')]").isEmpty());
        otherWorker.getPermissions().add(Permission.APPROVE_MELT_PASSPORT); userRepository.saveAndFlush(otherWorker);
        call(get("/api/tasks/"+approval.getId()),otherWorker).andExpect(status().isOk());
        call(post("/api/melt-passports/"+m.id()+"/approve"),outsider,decision(m)).andExpect(status().isForbidden());
    }

    @Test void actorsWithoutAssignmentOrEditRightsCannotModifyDraft() throws Exception {
        var m = assigned();
        call(post("/api/melt-passports/"+m.id()+"/assignment"),worker,new MeltAssignmentRequest(otherWorker.getId(),m.version())).andExpect(status().isForbidden());
        var r = new MeltPassportRequest(); r.setVersion(m.version()); r.setPieceIds(List.of(first.getId()));
        call(put("/api/melt-passports/"+m.id()),otherWorker,r).andExpect(status().isForbidden());
        call(post("/api/melt-passports/"+m.id()+"/calculate-magnesium"),otherWorker,decision(m)).andExpect(status().isForbidden());
        call(post("/api/melt-passports/"+m.id()+"/submit"),otherWorker,decision(m)).andExpect(status().isForbidden());
        call(post("/api/melt-passports/"+m.id()+"/assignment"),admin,new MeltAssignmentRequest(disabledWorker.getId(),m.version())).andExpect(status().isBadRequest());
        call(post("/api/melt-passports/"+m.id()+"/assignment"),admin,new MeltAssignmentRequest(outsider.getId(),m.version())).andExpect(status().isBadRequest());
    }

    @Test void delegatedAssignerCanUseBothRoutesWithoutTaskManagementOrDraftEditRights() throws Exception {
        var assigner = newUser("melt-assignment-" + UUID.randomUUID(), Role.OPERATOR, Set.of(Permission.ASSIGN_MELT_OPERATOR));
        String base = "/api/melt-passports";
        call(get(base + "/assignees"), worker).andExpect(status().isForbidden());
        call(get("/api/tasks/assignees"), assigner).andExpect(status().isForbidden());
        call(get(base + "/assignees"), assigner).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + worker.getId() + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == '" + disabledWorker.getId() + "')]").isEmpty());
        call(post(base + "/piece/" + first.getId() + "/assignment"), assigner, new MeltAssignmentRequest(worker.getId(), null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignable").value(true))
                .andExpect(jsonPath("$.editable").value(false)).andExpect(jsonPath("$.assignedOperatorId").value(worker.getId().toString()));
        var m = service.forPiece(first.getId(), assigner.getUsername()).get(0);
        var body = new MeltPassportRequest();
        body.setVersion(m.version()); body.setPieceIds(List.of(first.getId()));
        call(put(base + "/" + m.id()), assigner, body).andExpect(status().isForbidden());
        call(post(base + "/" + m.id() + "/assignment"), assigner, new MeltAssignmentRequest(otherWorker.getId(), m.version()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedOperatorId").value(otherWorker.getId().toString()))
                .andExpect(jsonPath("$.events[?(@.action == 'OPERATOR_ASSIGNED' && @.actorId == '" + assigner.getId() + "')]").isNotEmpty());
        assertEquals(otherWorker.getId(), task(m, TaskType.MELT_PASSPORT_ENTRY).getAssignedTo().getId());
    }

    @Test void reviewerMayEditAndResubmitOnlyAfterReturnWithoutTakingTheAssignment() throws Exception {
        var m = fill(assigned(), first);
        var request = new MeltPassportRequest();
        request.setVersion(m.version()); request.setPieceIds(List.of(first.getId()));
        call(put("/api/melt-passports/" + m.id()), outsider, request).andExpect(status().isForbidden());
        m = service.submit(m.id(), decision(m), worker.getUsername());
        request.setVersion(m.version());
        call(put("/api/melt-passports/" + m.id()), outsider, request).andExpect(status().isForbidden());

        m = service.returnForCorrection(m.id(), new MeltDecisionRequest(m.version(), "اصلاح بچ"), outsider.getUsername());
        assertTrue(m.editable());
        request.setVersion(m.version());
        request.setMeltDate(m.meltDate()); request.setFurnaceNumber(BigDecimal.valueOf(m.furnaceNumber())); request.setIngotType(m.ingotType());
        request.setPrimaryWeight(m.primaryWeight()); request.setRecycledWeight(m.recycledWeight());
        request.setInjectionOperator(m.injectionOperator());
        request.setChecks(m.checks());
        request.setPrimaryBatch("BATCH-REVISED");
        call(put("/api/melt-passports/" + m.id()), outsider, request).andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryBatch").value("BATCH-REVISED"))
                .andExpect(jsonPath("$.events[?(@.action == 'UPDATED' && @.actorId == '" + outsider.getId() + "')]").isNotEmpty());
        m = service.get(m.id(), outsider.getUsername());
        call(post("/api/melt-passports/" + m.id() + "/submit"), outsider, decision(m)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.assignedOperatorId").value(worker.getId().toString()));
        assertEquals(PieceStage.CASTING, first.getCurrentStage());
        assertEquals(worker.getId(), task(m, TaskType.MELT_PASSPORT_ENTRY).getAssignedTo().getId());
    }

    @Test void workflowTasksCannotBeCompletedOrReassignedThroughGenericEndpoints() throws Exception {
        var m=assigned(); var entry=task(m,TaskType.MELT_PASSPORT_ENTRY);
        call(patch("/api/tasks/"+entry.getId()+"/status"),worker,Map.of("status","COMPLETED")).andExpect(status().isBadRequest());
        call(patch("/api/tasks/"+entry.getId()+"/assignee"),admin,Map.of("assigneeId",otherWorker.getId())).andExpect(status().isBadRequest());
        m=fill(m,first);m=service.submit(m.id(),decision(m),worker.getUsername());
        var review=task(m,TaskType.MELT_PASSPORT_APPROVAL);
        call(patch("/api/tasks/"+review.getId()+"/status"),admin,Map.of("status","COMPLETED")).andExpect(status().isBadRequest());
        assertEquals(MeltPassportStatus.PENDING_APPROVAL,service.get(m.id(),admin.getUsername()).status());
    }

    @Test void staleAssignmentAndPendingReassignmentCannotChangeOwner() throws Exception {
        var m=assigned(); var previous=m;
        m=service.assign(m.id(),new MeltAssignmentRequest(otherWorker.getId(),m.version()),admin.getUsername());
        call(post("/api/melt-passports/"+m.id()+"/assignment"),admin,new MeltAssignmentRequest(worker.getId(),previous.version())).andExpect(status().isConflict());
        m=service.assign(m.id(),new MeltAssignmentRequest(worker.getId(),m.version()),admin.getUsername());
        m=fill(m,first);m=service.submit(m.id(),decision(m),worker.getUsername());
        call(post("/api/melt-passports/"+m.id()+"/assignment"),admin,new MeltAssignmentRequest(otherWorker.getId(),m.version())).andExpect(status().isBadRequest());
        assertEquals(worker.getId(),service.get(m.id(),admin.getUsername()).assignedOperatorId());
    }
}
