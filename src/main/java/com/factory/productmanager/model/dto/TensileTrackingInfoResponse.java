package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.TensileNextStage;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Display-only information from a persisted disposition, never a production substage. */
public record TensileTrackingInfoResponse(UUID tensileTestId, TensileNextStage nextStage,
                                          BigDecimal agingDurationMinutes,
                                          List<BigDecimal> agingDurationsMinutes) {}
