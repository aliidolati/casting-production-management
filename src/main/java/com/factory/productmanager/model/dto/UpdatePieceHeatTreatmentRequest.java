package com.factory.productmanager.model.dto;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;
@Getter @Setter
public class UpdatePieceHeatTreatmentRequest {
 @Size(max=50) private String treatmentStage;
 @Size(max=20) private String testPlateLabel;
 @DecimalMin("0") private BigDecimal testPlateHardnessHb;
 @Size(max=10000) private String details;
 private UUID tensileDispositionId;
}
