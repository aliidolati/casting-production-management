package com.factory.productmanager.service;

import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.NotificationFeedResponse;
import com.factory.productmanager.model.dto.NotificationItemResponse;
import com.factory.productmanager.model.dto.UnreadCountResponse;
import com.factory.productmanager.model.entity.NotificationType;
import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.PieceDefinition;
import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.model.entity.Role;
import com.factory.productmanager.model.entity.SystemNotification;
import com.factory.productmanager.model.entity.SystemNotificationRead;
import com.factory.productmanager.model.entity.TensileTest;
import com.factory.productmanager.model.entity.TensileNextStage;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.SystemNotificationReadRepository;
import com.factory.productmanager.repository.SystemNotificationRepository;
import com.factory.productmanager.repository.TensileTestRepository;
import com.factory.productmanager.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int DEFAULT_LIMIT = 40;
    private static final int MAX_LIMIT = 100;
    private final AtomicBoolean backfilled = new AtomicBoolean(false);

    private final SystemNotificationRepository notificationRepository;
    private final SystemNotificationReadRepository readRepository;
    private final UserRepository userRepository;
    private final TensileTestRepository tensileTestRepository;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;

    /**
     * ثبت اعلان تایید خودکار کشش. اگر برای همین تست قبلاً اعلان ساخته شده باشد
     * دوباره ساخته نمی‌شود.
     */
    public void createTensileAutoApproved(TensileTest test) {
        if (test == null || test.getId() == null) {
            return;
        }
        if (notificationRepository.existsByTensileTestId(test.getId())) {
            return;
        }

        Piece piece = test.getPiece();
        PieceDefinition definition = piece.getPieceDefinition();
        String pieceName = definition.getPieceName();
        Integer pieceNumber = piece.getPieceNumber();
        String projectCode = definition.getProject() == null ? null : definition.getProject().getCode();

        SystemNotification notification = new SystemNotification();
        notification.setType(NotificationType.TENSILE_AUTO_APPROVED);
        notification.setMessage(buildTensileAutoApprovedMessage(pieceName, pieceNumber));
        // فقط مرجع شناسه؛ گراف کامل قطعه (notes و بقیه کالکشن‌ها) را به نشست اعلان وصل نکن
        notification.setPiece(entityManager.getReference(Piece.class, piece.getId()));
        notification.setTensileTest(entityManager.getReference(TensileTest.class, test.getId()));
        notification.setPieceName(pieceName);
        notification.setPieceNumber(pieceNumber);
        notification.setProjectCode(projectCode);
        notificationRepository.save(notification);
    }

    public void deleteByTensileTestId(UUID tensileTestId) {
        notificationRepository.deleteByTensileTestId(tensileTestId);
    }

    @Transactional(readOnly = true)
    public NotificationFeedResponse listForUser(String username, Integer limit) {
        User user = findUser(username);
        boolean broadcast = canSeeBroadcast(user);
        int size = normalizeLimit(limit);
        List<SystemNotification> notifications =
                notificationRepository.findVisibleForUser(user.getId(), broadcast, PageRequest.of(0, size));
        Set<UUID> readIds = readIdsFor(user.getId(), notifications.stream().map(SystemNotification::getId).toList());
        List<NotificationItemResponse> items = notifications.stream()
                .map(n -> toItem(n, readIds.contains(n.getId())))
                .toList();
        return new NotificationFeedResponse(notificationRepository.countUnreadForUser(user.getId(), broadcast), items);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCountForUser(String username) {
        User user = findUser(username);
        return new UnreadCountResponse(notificationRepository.countUnreadForUser(user.getId(), canSeeBroadcast(user)));
    }

    public void markRead(UUID notificationId, String username) {
        User user = findUser(username);
        SystemNotification notification = findVisibleNotification(notificationId, user);
        if (readRepository.existsByNotificationIdAndUserId(notification.getId(), user.getId())) {
            return;
        }
        SystemNotificationRead read = new SystemNotificationRead();
        read.setNotification(notification);
        read.setUser(user);
        read.setReadAt(LocalDateTime.now());
        readRepository.save(read);
    }

    public void dismiss(UUID notificationId, String username) {
        User user = findUser(username);
        SystemNotification notification = findVisibleNotification(notificationId, user);
        SystemNotificationRead read = readRepository
                .findByNotificationIdAndUserId(notification.getId(), user.getId())
                .orElseGet(() -> {
                    SystemNotificationRead created = new SystemNotificationRead();
                    created.setNotification(notification);
                    created.setUser(user);
                    created.setReadAt(LocalDateTime.now());
                    return created;
                });
        read.setDismissed(true);
        readRepository.save(read);
    }

    public void markAllRead(String username) {
        User user = findUser(username);
        List<SystemNotification> unread = notificationRepository.findUnreadForUser(user.getId(), canSeeBroadcast(user));
        LocalDateTime now = LocalDateTime.now();
        for (SystemNotification notification : unread) {
            SystemNotificationRead read = new SystemNotificationRead();
            read.setNotification(notification);
            read.setUser(user);
            read.setReadAt(now);
            readRepository.save(read);
        }
    }

    /**
     * تست‌های خودکار قبلی که قبل از این ویژگی ثبت شده‌اند هم اعلان می‌گیرند
     * تا صندوق خالی نماند. روی خودِ ApplicationReady تراکنش باز نمی‌ماند تا
     * flush گراف JOIN FETCH قطعه استارت برنامه را خراب نکند.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void backfillTensileAutoApprovals() {
        if (!backfilled.compareAndSet(false, true)) {
            return;
        }
        try {
            List<UUID> testIds = tensileTestRepository.findSystemAutoApprovedTestIds(TensileNextStage.APPROVED);
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            for (UUID testId : testIds) {
                try {
                    tx.executeWithoutResult(status -> createTensileAutoApprovedById(testId));
                } catch (Exception ex) {
                    log.warn("اعلان تایید خودکار برای تست {} ساخته نشد", testId, ex);
                }
            }
        } catch (Exception ex) {
            log.warn("بازگردانی اعلان‌های تایید خودکار کشش انجام نشد", ex);
        }
    }

    private void createTensileAutoApprovedById(UUID testId) {
        TensileTest test = tensileTestRepository.findById(testId).orElse(null);
        if (test == null) {
            return;
        }
        createTensileAutoApproved(test);
    }

    static String buildTensileAutoApprovedMessage(String pieceName, Integer pieceNumber) {
        return "قطعه «" + pieceName + "» با شماره " + pieceNumber
                + " آزمون کشش‌اش توسط سیستم تأیید شد و به مرحله بعد انتقال داده شد";
    }

    private Set<UUID> readIdsFor(UUID userId, List<UUID> notificationIds) {
        if (notificationIds.isEmpty()) {
            return Set.of();
        }
        return readRepository.findByUserIdAndNotificationIdIn(userId, notificationIds).stream()
                .map(r -> r.getNotification().getId())
                .collect(Collectors.toSet());
    }

    private NotificationItemResponse toItem(SystemNotification notification, boolean read) {
        Piece piece = notification.getPiece();
        TensileTest test = notification.getTensileTest();
        return new NotificationItemResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                piece == null ? null : piece.getId(),
                notification.getPieceName(),
                notification.getPieceNumber(),
                notification.getProjectCode(),
                test == null ? null : test.getId(),
                notification.getTask() == null ? null : notification.getTask().getId(),
                notification.getCreatedAt(),
                read
        );
    }

    /** اعلان‌های همگانی (کشش) فقط برای ادمین و دارندگان دسترسی تعیین‌تکلیف کشش نمایش داده می‌شوند */
    private static boolean canSeeBroadcast(User user) {
        return user.getRole() == Role.MANAGER
                || (user.getPermissions() != null && user.getPermissions().contains(Permission.REVIEW_TENSILE_TEST));
    }

    /**
     * اعلان باید برای همین کاربر قابل‌مشاهده باشد؛ وگرنه حتی وجودش لو نمی‌رود (۴۰۴)
     * تا کسی با حدس‌زدن شناسه، اعلان دیگران را نخواند یا علامت‌گذاری نکند.
     */
    private SystemNotification findVisibleNotification(UUID notificationId, User user) {
        SystemNotification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("اعلان پیدا نشد"));
        User recipient = notification.getRecipient();
        boolean visible = recipient == null
                ? canSeeBroadcast(user)
                : recipient.getId().equals(user.getId());
        if (!visible) {
            throw new ResourceNotFoundException("اعلان پیدا نشد");
        }
        return notification;
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر پیدا نشد"));
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
