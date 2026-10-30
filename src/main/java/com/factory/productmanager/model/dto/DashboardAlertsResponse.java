package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardAlertsResponse {
    private List<NeedsWeldingAlertItem> needsWelding;
    private List<StuckPieceAlertItem> stuckPieces;
    private int stuckThresholdDays;
}
