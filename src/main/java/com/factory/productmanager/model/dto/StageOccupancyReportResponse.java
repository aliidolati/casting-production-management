package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StageOccupancyReportResponse {
    private UUID projectId;
    private String projectCode;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private List<StageOccupancyRow> rows = new ArrayList<>();
}
