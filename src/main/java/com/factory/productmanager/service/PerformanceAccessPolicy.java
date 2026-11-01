package com.factory.productmanager.service;

import com.factory.productmanager.exception.*;
import com.factory.productmanager.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class PerformanceAccessPolicy {
    private final TaskAccessPolicy taskPolicy;
    public boolean canManage(User actor) { return taskPolicy.isManager(actor); }
    public void requireVisible(User actor, UUID userId) {
        if (!actor.getId().equals(userId) && !canManage(actor))
            throw new ResourceNotFoundException("ارزیابی پیدا نشد");
    }
    public void requireManage(User actor, UUID userId) {
        requireVisible(actor, userId);
        if (!canManage(actor)) throw new ForbiddenOperationException("فقط ادمین می‌تواند ارزیابی را تغییر دهد");
    }
}
