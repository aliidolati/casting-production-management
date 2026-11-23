package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Retained when an individual result is deleted so replaying a batch cannot recreate it. */
@Entity
@Table(name = "spectrometry_batch_receipts", uniqueConstraints = @UniqueConstraint(
        name = "uk_spectrometry_batch_request", columnNames = {"actor_id", "request_id"}))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@AllArgsConstructor
public class SpectrometryBatchReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(nullable = false, updatable = false, length = 64)
    private String fingerprint;

    @Column(nullable = false, updatable = false)
    private int rowCount;
}
