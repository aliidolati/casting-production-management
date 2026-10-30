package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * یک رویداد ساخت‌یافته در تاریخچه‌ی وظیفه (چه کسی، چه کاری، چه زمانی، مقدار قبلی و جدید).
 * رکوردها فقط اضافه می‌شوند و هرگز ویرایش نمی‌شوند.
 * oldValue/newValue متن خوانا هستند (نام وضعیت، نام کاربر، کد پروژه، ...) تا حتی بعد از
 * تغییر نام کاربر یا حذف ارجاع، تاریخچه همان چیزی را نشان دهد که در لحظه‌ی رویداد بوده است.
 */
@Entity
@Table(
        name = "task_activities",
        indexes = {
                @Index(name = "idx_task_activities_task_created", columnList = "task_id, created_at"),
                @Index(name = "idx_task_activities_actor", columnList = "actor_id"),
                @Index(name = "idx_task_activities_action_created", columnList = "action, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TaskActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 48, columnDefinition = "varchar(48)")
    private TaskActivityAction action;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    /** توضیح اختیاری (مثلاً دلیل بازگشایی وظیفه) */
    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
