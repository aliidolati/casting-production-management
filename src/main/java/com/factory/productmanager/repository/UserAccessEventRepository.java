package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.UserAccessEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserAccessEventRepository extends JpaRepository<UserAccessEvent, UUID> {
    Page<UserAccessEvent> findByTargetId(UUID targetId, Pageable pageable);
}
