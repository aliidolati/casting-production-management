package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.PieceStage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * هشدار داشبورد برای قطعه‌ای که هم‌اکنون در ایستگاه جوشکاری است.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NeedsWeldingAlertItem {
    private UUID pieceId;
    private String projectCode;
    private String pieceName;
    private Integer pieceNumber;
    private PieceStage currentStage;
    private String defectType;
    private String notes;
    private String sentBy;
    private LocalDateTime sentAt;
    private List<NeedsWeldingDefectDetail> defects = new ArrayList<>();
}
