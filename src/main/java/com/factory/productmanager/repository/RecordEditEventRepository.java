package com.factory.productmanager.repository;
import com.factory.productmanager.model.entity.RecordEditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.UUID;
public interface RecordEditEventRepository extends JpaRepository<RecordEditEvent,UUID> {
 Page<RecordEditEvent> findByKindAndTargetId(String kind, UUID targetId, Pageable page);
}
