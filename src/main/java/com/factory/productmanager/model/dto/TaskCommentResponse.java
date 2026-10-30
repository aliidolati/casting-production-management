package com.factory.productmanager.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskCommentResponse(
        UUID id,
        UserRefResponse author,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean edited,
        boolean canEdit,
        boolean canDelete
) {
}
