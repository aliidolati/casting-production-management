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
public class RadiographyDefectReportRow {
    private String projectCode;
    private UUID pieceDefinitionId;
    private Integer rowNum;
    private String pieceName;
    private String defectType;
    private long defectCount;
    private long piecesWithDefect;
    private long totalPiecesInRow;
    private Double percentPiecesWithDefect;
    /** قطعات دارای حداقل یک عیب از این نوع (مطابق فیلترها) */
    private List<RadiographyDefectReportPieceItem> pieces = new ArrayList<>();
}
