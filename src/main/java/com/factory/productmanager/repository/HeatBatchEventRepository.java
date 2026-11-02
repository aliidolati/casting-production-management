package com.factory.productmanager.repository;
import com.factory.productmanager.model.entity.HeatBatchEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.UUID;
public interface HeatBatchEventRepository extends JpaRepository<HeatBatchEvent,UUID>{Page<HeatBatchEvent> findByBatchId(UUID id,Pageable page);}
