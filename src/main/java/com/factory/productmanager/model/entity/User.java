package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@org.hibernate.annotations.DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name")
    private String fullName;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private Role role;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "permission", length = 64)
    private Set<Permission> permissions = new HashSet<>();

    /** بخش/واحد سازمانی (اختیاری)؛ فقط برای نمایش در پروفایل */
    @Column(name = "department", length = 100)
    private String department;

    /**
     * وضعیت حساب. عمداً به‌صورت «غیرفعال بودن» ذخیره می‌شود تا ستون جدید روی ردیف‌های
     * موجود (که مقدار پیش‌فرض false می‌گیرند) هیچ کاربر فعلی را قفل نکند.
     */
    @Column(name = "disabled", nullable = false)
    private boolean disabled = false;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    /** مسیر نسبی تصویر امضای کاربر (فقط خود کاربر آن را ثبت می‌کند)؛ برای تأییدهای رادیوگرافی استفاده می‌شود */
    @Column(name = "signature_path", length = 255)
    private String signaturePath;

    @Column(name = "signature_content_type", length = 64)
    private String signatureContentType;

    @Column(name = "signature_updated_at")
    private LocalDateTime signatureUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
