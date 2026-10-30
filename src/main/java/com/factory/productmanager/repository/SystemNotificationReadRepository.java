package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.SystemNotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemNotificationReadRepository extends JpaRepository<SystemNotificationRead, UUID> {

    boolean existsByNotificationIdAndUserId(UUID notificationId, UUID userId);

    Optional<SystemNotificationRead> findByNotificationIdAndUserId(UUID notificationId, UUID userId);

    List<SystemNotificationRead> findByUserIdAndNotificationIdIn(UUID userId, Collection<UUID> notificationIds);
}
