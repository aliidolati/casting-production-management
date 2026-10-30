package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * هشدار داشبورد برای قطعه‌ای که مدت زیادی (بیشتر از آستانه‌ی تعیین‌شده) بدون تغییر وضعیت مانده است.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StuckPieceAlertItem {
    private UUID pieceId;
    private String projectCode;
    private String pieceName;
    private Integer pieceNumber;
    private PieceStage currentStage;
    private long daysInStage;
}
