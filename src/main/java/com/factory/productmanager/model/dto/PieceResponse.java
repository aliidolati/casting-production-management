package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.HeatTreatmentSubStage;
import com.factory.productmanager.model.entity.RadiographySubStage;
import com.factory.productmanager.model.entity.RoughMachiningSubStage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PieceResponse {
    private UUID id;
    private UUID pieceDefinitionId;
    private String pieceName;
    private Integer rowNum;
    private Integer pieceNumber;
    private PieceStage currentStage;
    private LocalDateTime createdAt;
    private UUID projectId ;
    private String projectCode ;
    private String description;
    private long defectsCount;
    private RadiographySubStage radiographySubStage;
    private HeatTreatmentSubStage heatTreatmentSubStage;
    private RoughMachiningSubStage roughMachiningSubStage;
    // تاریخی که قطعه به وضعیت فعلی‌اش تغییر پیدا کرده (اگر هنوز هیچ تغییر وضعیتی نداشته، تاریخ ثبت قطعه)
    private LocalDateTime currentStageEnteredAt;
    /** آیا تعریف قطعه‌ی مربوطه نقشه دو‌بعدی دارد؟ */
    private boolean hasDrawing;

    // آستانه‌های تست کشش از تعریف قطعه، تا فرم ثبت تست بتواند حد مجاز را نشان دهد
    private BigDecimal minUts;
    private BigDecimal minYs;
    private BigDecimal minEl;
    private BigDecimal minHb;
    private LocalDate processDate;
    private LocalDateTime archivedAt;
    /** مقصدهای مجاز بعدی برای کاربر فعلی؛ ادمین همهٔ مراحل قابل‌انتقال به‌جز وضعیت فعلی را می‌گیرد. */
    private List<PieceStage> allowedNextStages = new ArrayList<>();
    private List<RadiographySubStage> allowedNextRadiographySubStages = new ArrayList<>();
    private List<HeatTreatmentSubStage> allowedNextHeatTreatmentSubStages = new ArrayList<>();
    private List<RoughMachiningSubStage> allowedNextRoughMachiningSubStages = new ArrayList<>();
    /** قدم خطی بعدی (بدون اسقاط)؛ اگر ته خط باشد null است. */
    private ForwardMoveResponse forwardMove;
    /** Latest relevant repeat/aging decision; display information, not a substage. */
    private TensileTrackingInfoResponse tensileTrackingInfo;
}
