package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.RadiographyWorkflow;
import com.factory.productmanager.model.entity.RadiographyWorkflowAction;
import com.factory.productmanager.model.entity.RadiographyWorkflowEvent;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.RadiographyWorkflowEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** ثبت رویدادهای ممیزی گردش‌کار (فقط افزودن). در تراکنش فراخواننده اجرا می‌شود. */
@Component
@RequiredArgsConstructor
class RadiographyWorkflowEventRecorder {

    private final RadiographyWorkflowEventRepository eventRepository;

    void record(
            RadiographyWorkflow workflow,
            RadiographyWorkflowAction action,
            User actor,
            String oldValue,
            String newValue,
            String reason,
            LocalDateTime at
    ) {
        RadiographyWorkflowEvent event = new RadiographyWorkflowEvent();
        event.setWorkflow(workflow);
        event.setPieceId(workflow.getPiece().getId());
        event.setAction(action);
        if (actor != null) {
            event.setActorUserId(actor.getId());
            event.setActorName(WorkflowTaskOperations.name(actor));
        }
        event.setOldValue(truncate(oldValue, 255));
        event.setNewValue(truncate(newValue, 255));
        event.setReason(reason);
        event.setCreatedAt(at);
        eventRepository.save(event);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}