package com.factory.productmanager.service;

import com.factory.productmanager.exception.*;
import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class PerformanceAccessPolicy {
    private final TaskAccessPolicy taskPolicy;
    public boolean canManage(User actor) { return taskPolicy.isManager(actor); }
    public boolean canEditScores(User actor) {
        return canManage(actor) || actor.getPermissions().contains(Permission.EDIT_PERFORMANCE_SCORES);
    }
    public void requireVisible(User actor, UUID userId) {
        if (!actor.getId().equals(userId) && !canEditScores(actor))
            throw new ResourceNotFoundException("ارزیابی پیدا نشد");
    }
    public void requireManage(User actor, UUID userId) {
        requireVisible(actor, userId);
        if (!canManage(actor)) throw new ForbiddenOperationException("فقط مدیر می‌تواند ساختار ارزیابی را تغییر دهد");
    }
    public void requireScoreEditor(User actor, UUID userId) {
        requireVisible(actor, userId);
        if (!canEditScores(actor)) throw new ForbiddenOperationException("مجوز ویرایش نمرات ارزیابی لازم است");
    }
}
