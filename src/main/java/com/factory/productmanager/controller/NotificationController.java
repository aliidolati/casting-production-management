package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.NotificationFeedResponse;
import com.factory.productmanager.model.dto.UnreadCountResponse;
import com.factory.productmanager.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
// هر کاربر واردشده صندوق اعلان خودش را دارد؛ اینکه چه اعلان‌هایی ببیند (مثلاً اعلان‌های کشش فقط
// برای دارندگان REVIEW_TENSILE_TEST) در NotificationService تعیین می‌شود.
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<NotificationFeedResponse> list(
            @RequestParam(required = false) Integer limit,
            Authentication authentication
    ) {
        return ResponseEntity.ok(notificationService.listForUser(authentication.getName(), limit));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(notificationService.unreadCountForUser(authentication.getName()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, Authentication authentication) {
        notificationService.markRead(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        notificationService.markAllRead(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> dismiss(@PathVariable UUID id, Authentication authentication) {
        notificationService.dismiss(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
