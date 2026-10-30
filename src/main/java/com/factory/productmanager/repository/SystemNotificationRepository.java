package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.NotificationType;
import com.factory.productmanager.model.entity.SystemNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * «قابل‌مشاهده برای کاربر» یعنی: اعلان شخصیِ خودِ او، یا (فقط اگر includeBroadcast=true) اعلان همگانی؛
 * و در هر دو حالت توسط خودش پاک (dismiss) نشده باشد.
 * includeBroadcast را سرویس از روی دسترسی کاربر تعیین می‌کند.
 */
@Repository
public interface SystemNotificationRepository extends JpaRepository<SystemNotification, UUID> {

    boolean existsByTensileTestId(UUID tensileTestId);

    void deleteByTensileTestId(UUID tensileTestId);

    @Query("""
            SELECT n FROM SystemNotification n
            WHERE ((n.recipient IS NULL AND :includeBroadcast = true)
                   OR (n.recipient IS NOT NULL AND n.recipient.id = :userId))
              AND NOT EXISTS (
                SELECT 1 FROM SystemNotificationRead r
                WHERE r.notification = n AND r.user.id = :userId AND r.dismissed = true
              )
            ORDER BY n.createdAt DESC
            """)
    List<SystemNotification> findVisibleForUser(
            @Param("userId") UUID userId,
            @Param("includeBroadcast") boolean includeBroadcast,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(n) FROM SystemNotification n
            WHERE ((n.recipient IS NULL AND :includeBroadcast = true)
                   OR (n.recipient IS NOT NULL AND n.recipient.id = :userId))
              AND NOT EXISTS (
                SELECT 1 FROM SystemNotificationRead r
                WHERE r.notification = n AND r.user.id = :userId
              )
            """)
    long countUnreadForUser(@Param("userId") UUID userId, @Param("includeBroadcast") boolean includeBroadcast);

    @Query("""
            SELECT n FROM SystemNotification n
            WHERE ((n.recipient IS NULL AND :includeBroadcast = true)
                   OR (n.recipient IS NOT NULL AND n.recipient.id = :userId))
              AND NOT EXISTS (
                SELECT 1 FROM SystemNotificationRead r
                WHERE r.notification = n AND r.user.id = :userId
              )
            """)
    List<SystemNotification> findUnreadForUser(
            @Param("userId") UUID userId,
            @Param("includeBroadcast") boolean includeBroadcast
    );

    /** برای جلوگیری از اسپم: اعلان خوانده‌نشده‌ی هم‌نوع برای همین گیرنده و وظیفه (جدیدترین اول) */
    @Query("""
            SELECT n FROM SystemNotification n
            WHERE n.recipient.id = :userId
              AND n.task.id = :taskId
              AND n.type = :type
              AND NOT EXISTS (
                SELECT 1 FROM SystemNotificationRead r
                WHERE r.notification = n AND r.user.id = :userId
              )
            ORDER BY n.createdAt DESC
            """)
    List<SystemNotification> findUnreadByRecipientTaskAndType(
            @Param("userId") UUID userId,
            @Param("taskId") UUID taskId,
            @Param("type") NotificationType type,
            Pageable pageable
    );

    boolean existsByRecipientIdAndDedupeKey(UUID recipientId, String dedupeKey);
}
