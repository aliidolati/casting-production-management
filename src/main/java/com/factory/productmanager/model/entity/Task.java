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
 * وظیفه‌ی داخلی. فقط به کاربر، پروژه و قطعه‌ی موجود «ارجاع» می‌دهد و هیچ داده‌ی آن‌ها را کپی نمی‌کند.
 *
 * وضعیت «تأخیر» عمداً ذخیره نمی‌شود؛ از status و dueDate محاسبه می‌شود (TaskStatus.OPEN و dueDate &lt; now).
 *
 * برای اتصال‌های آینده (دسته‌ی تولید، گزارش رادیوگرافی، NCR، ...) فقط یک ستون/جدول ارجاع جدید
 * اضافه می‌شود؛ ساختار فعلی تغییر نمی‌کند.
 */
@Entity
@Table(
        name = "tasks",
        indexes = {
                @Index(name = "idx_tasks_assigned_to", columnList = "assigned_to"),
                @Index(name = "idx_tasks_created_by", columnList = "created_by"),
                @Index(name = "idx_tasks_status", columnList = "status"),
                @Index(name = "idx_tasks_priority", columnList = "priority"),
                @Index(name = "idx_tasks_due_date", columnList = "due_date"),
                @Index(name = "idx_tasks_project", columnList = "project_id"),
                @Index(name = "idx_tasks_part", columnList = "part_id"),
                // الگوی اصلی کوئری: «وظایف باز یک کاربر به ترتیب مهلت»
                @Index(name = "idx_tasks_assignee_status_due", columnList = "assigned_to, status, due_date"),
                @Index(name = "idx_tasks_status_due", columnList = "status, due_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
    private TaskPriority priority = TaskPriority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_to", nullable = false)
    private User assignedTo;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** با حذف پروژه/قطعه، وظیفه می‌ماند و فقط ارجاع خالی می‌شود */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Piece part;

    /**
     * نوع وظیفه؛ null (ردیف‌های قبلی) یعنی وظیفه‌ی عادی. ستون عمداً nullable است تا افزودنش
     * روی جدول پر هیچ ردیف موجودی را نامعتبر نکند.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "task_type", length = 48, columnDefinition = "varchar(48)")
    private TaskType taskType;

    /** شناسه‌ی گردش‌کار رادیوگرافی یا شناسنامه ذوب، بر اساس taskType؛ ارجاع ساده بدون FK */
    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isWorkflowTask() {
        return taskType != null && taskType.isWorkflow();
    }

    /** زمان‌ها توسط سرویس (با Clock برنامه) مقدار می‌گیرند؛ این فقط پشتیبان است */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = this.createdAt;
        }
    }
}
