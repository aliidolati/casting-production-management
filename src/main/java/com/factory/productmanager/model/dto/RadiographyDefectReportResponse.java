package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RadiographyDefectReportResponse {
    private UUID projectId;
    private String projectCode;
    private List<RadiographyDefectReportRow> rows = new ArrayList<>();
}
