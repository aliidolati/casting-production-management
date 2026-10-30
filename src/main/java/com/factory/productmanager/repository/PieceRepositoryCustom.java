package com.factory.productmanager.repository;

import com.factory.productmanager.model.dto.TrackingListFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PieceRepositoryCustom {
    Page<UUID> findTrackingPieceIds(TrackingListFilter filter, Pageable pageable);

    List<Object[]> countTrackingGroupedByStage(TrackingListFilter filter);
}
