package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RadiographyDefectReportPieceItem {
    private UUID pieceId;
    private Integer pieceNumber;
    private PieceStage currentStage;
    /** تعداد عیب از همان نوع (مطابق فیلترهای گزارش) روی این قطعه */
    private long defectCount;
    private long needsWeldingCount;
    private long approvedCount;
    private long weldingConfirmedCount;
}
