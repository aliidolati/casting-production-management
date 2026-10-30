package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.HeatTreatmentSubStage;
import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.RadiographySubStage;
import com.factory.productmanager.model.entity.RoughMachiningSubStage;

import java.util.List;

/**
 * مقصدهای مجاز بعدی برای یک قطعه. شامل وضعیت فعلی نیست؛ UI همان را جداگانه selected می‌گذارد.
 */
public record AllowedPieceMoves(
        List<PieceStage> stages,
        List<RadiographySubStage> radiographySubStages,
        List<HeatTreatmentSubStage> heatTreatmentSubStages,
        List<RoughMachiningSubStage> roughMachiningSubStages
) {
    public static AllowedPieceMoves of(
            List<PieceStage> stages,
            List<RadiographySubStage> radiographySubStages,
            List<HeatTreatmentSubStage> heatTreatmentSubStages,
            List<RoughMachiningSubStage> roughMachiningSubStages
    ) {
        return new AllowedPieceMoves(
                List.copyOf(stages),
                List.copyOf(radiographySubStages),
                List.copyOf(heatTreatmentSubStages),
                List.copyOf(roughMachiningSubStages)
        );
    }
}
