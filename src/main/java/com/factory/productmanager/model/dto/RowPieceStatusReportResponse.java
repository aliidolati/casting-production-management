package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RowPieceStatusReportResponse {
    private UUID pieceDefinitionId;
    private UUID projectId;
    private String projectCode;
    private String projectName;
    private Integer rowNum;
    private String pieceName;
    private int totalCount;
    private int activeCount;
    private int archivedCount;
    private List<StageStatusCount> stageCounts;
    private List<PieceResponse> pieces;
}
