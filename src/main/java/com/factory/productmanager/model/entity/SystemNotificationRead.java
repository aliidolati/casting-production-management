package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * وضعیت خوانده‌شدن یک اعلان برای یک کاربر مشخص.
 * خوانده‌شدن توسط ادمین، همان اعلان را برای مسئول کشش خوانده نمی‌کند.
 */
@Entity
@Table(
        name = "system_notification_reads",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_system_notification_read_user",
                columnNames = {"notification_id", "user_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class SystemNotificationRead {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private SystemNotification notification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    /** اگر روشن باشد این اعلان دیگر در صندوق همین کاربر دیده نمی‌شود */
    @Column(nullable = false)
    private boolean dismissed = false;

    @PrePersist
    protected void onCreate() {
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }
}
