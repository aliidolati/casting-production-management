package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.SpectrometryBatchReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpectrometryBatchReceiptRepository extends JpaRepository<SpectrometryBatchReceipt, UUID> {
    Optional<SpectrometryBatchReceipt> findByActorIdAndRequestId(UUID actorId, UUID requestId);
}
