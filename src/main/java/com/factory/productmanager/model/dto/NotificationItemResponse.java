package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationItemResponse {
    private UUID id;
    private NotificationType type;
    private String message;
    private UUID pieceId;
    private String pieceName;
    private Integer pieceNumber;
    private String projectCode;
    private UUID tensileTestId;
    /** فقط برای اعلان‌های وظایف پر است؛ کلیک روی اعلان به همین وظیفه می‌رود */
    private UUID taskId;
    private LocalDateTime createdAt;
    private boolean read;
}
