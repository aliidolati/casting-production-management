package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class TensileQualityReportResponse {
    private List<TensileQualityPointResponse> points;
    private TensileQualityStatsResponse stats;
}
