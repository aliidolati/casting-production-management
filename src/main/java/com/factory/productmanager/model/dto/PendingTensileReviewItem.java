package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** یک ردیف از ویجت داشبورد «نیازمند تعیین‌تکلیف کشش». */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingTensileReviewItem {
    private UUID tensileTestId;
    private Integer testIndex;
    private UUID pieceId;
    private Integer pieceNumber;
    private UUID pieceDefinitionId;
    private String pieceName;
    private UUID projectId;
    private String projectCode;
    private boolean belowThreshold;
    private LocalDateTime createdAt;
    private String createdBy;
    /** چند روز است که این تست منتظر تعیین‌تکلیف مانده */
    private long waitingDays;
}
