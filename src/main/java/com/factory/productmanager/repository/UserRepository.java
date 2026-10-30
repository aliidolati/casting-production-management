package com.factory.productmanager.repository;

import com.factory.productmanager.model.dto.UserRefResponse;
import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockForPerformance(@Param("id") UUID id);

    Optional<User> findByUsername(String username);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    Optional<User> lockForCredentialsByUsername(@Param("username") String username);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockForCredentialsById(@Param("id") UUID id);

    boolean existsByUsername(String username);

    @Modifying
    @Query("UPDATE User u SET u.lastActivityAt = :at WHERE u.username = :username")
    int updateLastActivity(@Param("username") String username, @Param("at") LocalDateTime at);

    /**
     * کاربرانی که می‌توانند مسئول وظیفه شوند (حساب فعال + ادمین یا دارای RECEIVE_TASKS).
     * به‌صورت پروجکشن خوانده می‌شود تا کالکشن دسترسی‌های هر کاربر بارگذاری نشود.
     */
    @Query("""
            SELECT new com.factory.productmanager.model.dto.UserRefResponse(u.id, u.username, u.fullName, u.department)
            FROM User u
            WHERE u.disabled = false
              AND (u.role = com.factory.productmanager.model.entity.Role.ADMIN
                   OR :permission MEMBER OF u.permissions)
            ORDER BY LOWER(COALESCE(u.fullName, u.username))
            """)
    List<UserRefResponse> findAssignableUsers(@Param("permission") Permission permission);
}
