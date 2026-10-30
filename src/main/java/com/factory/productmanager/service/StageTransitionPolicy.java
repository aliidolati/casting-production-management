package com.factory.productmanager.service;

import com.factory.productmanager.model.dto.AllowedPieceMoves;
import com.factory.productmanager.model.dto.ForwardMoveKind;
import com.factory.productmanager.model.dto.ForwardMoveResponse;
import com.factory.productmanager.model.entity.HeatTreatmentSubStage;
import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.PieceStage;
import com.factory.productmanager.model.entity.RadiographySubStage;
import com.factory.productmanager.model.entity.RoughMachiningSubStage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * گراف کنترل‌شدهٔ انتقال قطعات. کاربران عادی فقط رو به جلو (+ اسقاط) حرکت می‌کنند؛
 * ادمین برای اصلاح از گراف معاف است. ورود به جوشکاری از این سیاست خارج است.
 */
@Component
public class StageTransitionPolicy {

    static final List<PieceStage> TRANSFERABLE_STAGES = List.of(
            PieceStage.NOT_STARTED,
            PieceStage.CASTING,
            PieceStage.ROUGH_MACHINING,
            PieceStage.RADIOGRAPHY,
            PieceStage.HEAT_TREATMENT,
            PieceStage.TENSILE,
            PieceStage.SHOT_BLASTING,
            PieceStage.READY,
            PieceStage.DELIVERED,
            PieceStage.SCRAPPED
    );

    public AllowedPieceMoves allowedMoves(Piece piece, boolean admin) {
        return admin ? adminMoves(piece) : userMoves(piece);
    }

    public void assertStageAllowed(Piece piece, PieceStage target, boolean admin) {
        if (target == PieceStage.WELDING) {
            throw new IllegalStateException(
                    "جوشکاری ایستگاه است نه مرحله؛ از بخش ارسال به جوشکاری در پروفایل قطعه استفاده کنید"
            );
        }
        if (piece.getCurrentStage() == target) {
            throw new IllegalStateException("این قطعه هم‌اکنون در همین مرحله است و انتقال تکراری مجاز نیست");
        }
        if (admin) {
            return;
        }
        if (!userMoves(piece).stages().contains(target)) {
            throw new IllegalStateException("انتقال به این مرحله در روال تولید مجاز نیست");
        }
    }

    public void assertRadiographySubStageAllowed(Piece piece, RadiographySubStage target, boolean admin) {
        if (piece.getCurrentStage() != PieceStage.RADIOGRAPHY) {
            throw new IllegalStateException(
                    "زیرمرحله‌ی رادیوگرافی فقط برای قطعاتی که در مرحله‌ی رادیوگرافی هستند قابل تغییر است"
            );
        }
        if (piece.getRadiographySubStage() == target) {
            throw new IllegalStateException(
                    "این قطعه هم‌اکنون در همین زیرمرحلهٔ رادیوگرافی است و انتقال تکراری مجاز نیست"
            );
        }
        if (admin) {
            return;
        }
        if (!userMoves(piece).radiographySubStages().contains(target)) {
            throw new IllegalStateException("انتقال به این زیرمرحلهٔ رادیوگرافی در روال تولید مجاز نیست");
        }
    }

    public void assertHeatTreatmentSubStageAllowed(Piece piece, HeatTreatmentSubStage target, boolean admin) {
        if (piece.getCurrentStage() != PieceStage.HEAT_TREATMENT) {
            throw new IllegalStateException(
                    "زیرمرحله‌ی عملیات حرارتی فقط برای قطعاتی که در مرحله‌ی عملیات حرارتی هستند قابل تغییر است"
            );
        }
        if (piece.getHeatTreatmentSubStage() == target) {
            throw new IllegalStateException(
                    "این قطعه هم‌اکنون در همین زیرمرحلهٔ عملیات حرارتی است و انتقال تکراری مجاز نیست"
            );
        }
        if (admin) {
            return;
        }
        if (!userMoves(piece).heatTreatmentSubStages().contains(target)) {
            throw new IllegalStateException("انتقال به این زیرمرحلهٔ عملیات حرارتی در روال تولید مجاز نیست");
        }
    }

    public void assertRoughMachiningSubStageAllowed(Piece piece, RoughMachiningSubStage target, boolean admin) {
        if (piece.getCurrentStage() != PieceStage.ROUGH_MACHINING) {
            throw new IllegalStateException(
                    "زیرمرحله‌ی خشن‌تراشی فقط برای قطعاتی که در مرحله‌ی خشن‌تراشی هستند قابل تغییر است"
            );
        }
        if (piece.getRoughMachiningSubStage() == target) {
            throw new IllegalStateException(
                    "این قطعه هم‌اکنون در همین زیرمرحلهٔ خشن‌تراشی است و انتقال تکراری مجاز نیست"
            );
        }
        if (admin) {
            return;
        }
        if (!userMoves(piece).roughMachiningSubStages().contains(target)) {
            throw new IllegalStateException("انتقال به این زیرمرحلهٔ خشن‌تراشی در روال تولید مجاز نیست");
        }
    }

    private AllowedPieceMoves adminMoves(Piece piece) {
        PieceStage current = piece.getCurrentStage();
        List<PieceStage> stages = TRANSFERABLE_STAGES.stream()
                .filter(s -> s != current)
                .toList();

        List<RadiographySubStage> radio = List.of();
        List<HeatTreatmentSubStage> heat = List.of();
        List<RoughMachiningSubStage> rough = List.of();

        if (current == PieceStage.RADIOGRAPHY) {
            RadiographySubStage now = radioOf(piece);
            radio = List.of(RadiographySubStage.values()).stream().filter(s -> s != now).toList();
        } else if (current == PieceStage.HEAT_TREATMENT) {
            HeatTreatmentSubStage now = heatOf(piece);
            heat = List.of(HeatTreatmentSubStage.values()).stream().filter(s -> s != now).toList();
        } else if (current == PieceStage.ROUGH_MACHINING) {
            RoughMachiningSubStage now = roughOf(piece);
            rough = List.of(RoughMachiningSubStage.values()).stream().filter(s -> s != now).toList();
        }

        return AllowedPieceMoves.of(stages, radio, heat, rough);
    }

    private AllowedPieceMoves userMoves(Piece piece) {
        PieceStage current = piece.getCurrentStage();
        List<PieceStage> stages = new ArrayList<>();
        List<RadiographySubStage> radio = new ArrayList<>();
        List<HeatTreatmentSubStage> heat = new ArrayList<>();
        List<RoughMachiningSubStage> rough = new ArrayList<>();

        switch (current) {
            case NOT_STARTED -> stages.add(PieceStage.CASTING);
            case CASTING -> stages.add(PieceStage.ROUGH_MACHINING);
            case ROUGH_MACHINING -> {
                RoughMachiningSubStage now = roughOf(piece);
                if (now == RoughMachiningSubStage.DEBURRING) {
                    rough.add(RoughMachiningSubStage.INITIAL_CORRECTION);
                } else if (now == RoughMachiningSubStage.INITIAL_CORRECTION) {
                    rough.add(RoughMachiningSubStage.MACHINING);
                } else {
                    stages.add(PieceStage.RADIOGRAPHY);
                }
            }
            case RADIOGRAPHY -> {
                RadiographySubStage now = radioOf(piece);
                if (now == RadiographySubStage.SHOT) {
                    radio.add(RadiographySubStage.INTERPRETATION);
                } else if (now == RadiographySubStage.INTERPRETATION) {
                    radio.add(RadiographySubStage.VT);
                } else {
                    stages.add(PieceStage.HEAT_TREATMENT);
                }
            }
            case HEAT_TREATMENT -> {
                HeatTreatmentSubStage now = heatOf(piece);
                switch (now) {
                    case AWAITING_OPERATIONS -> heat.add(HeatTreatmentSubStage.STAGE_1);
                    case STAGE_1 -> heat.add(HeatTreatmentSubStage.STAGE_2);
                    case STAGE_2 -> heat.add(HeatTreatmentSubStage.CNC_SAMPLE);
                    case CNC_SAMPLE, STAGE_3, STAGE_4, STAGE_5 -> stages.add(PieceStage.TENSILE);
                }
            }
            case SHOT_BLASTING -> stages.add(PieceStage.READY);
            case READY -> stages.add(PieceStage.DELIVERED);
            case TENSILE, DELIVERED, WELDING -> {
                // فقط اسقاط؛ تعیین‌تکلیف کشش و اتمام جوش مسیر جدا دارند
            }
            case SCRAPPED -> {
                return AllowedPieceMoves.of(List.of(), List.of(), List.of(), List.of());
            }
        }

        if (current != PieceStage.SCRAPPED) {
            stages.add(PieceStage.SCRAPPED);
        }

        return AllowedPieceMoves.of(stages, radio, heat, rough);
    }

    /**
     * یک قدم رو به جلو در خط تولید، بدون اسقاط.
     * برای شروع‌نشده تاریخ انجام فرایند لازم نیست.
     */
    public ForwardMoveResponse forwardMove(Piece piece) {
        if (piece == null || piece.getCurrentStage() == null) {
            return null;
        }
        boolean dateRequired = piece.getCurrentStage() != PieceStage.NOT_STARTED;
        return switch (piece.getCurrentStage()) {
            case NOT_STARTED -> stageMove(PieceStage.CASTING, null, null, null, false);
            case CASTING -> stageMove(
                    PieceStage.ROUGH_MACHINING,
                    null,
                    null,
                    RoughMachiningSubStage.DEBURRING,
                    true
            );
            case ROUGH_MACHINING -> {
                RoughMachiningSubStage now = roughOf(piece);
                if (now == RoughMachiningSubStage.DEBURRING) {
                    yield roughMove(RoughMachiningSubStage.INITIAL_CORRECTION, dateRequired);
                }
                if (now == RoughMachiningSubStage.INITIAL_CORRECTION) {
                    yield roughMove(RoughMachiningSubStage.MACHINING, dateRequired);
                }
                yield stageMove(PieceStage.RADIOGRAPHY, RadiographySubStage.SHOT, null, null, dateRequired);
            }
            case RADIOGRAPHY -> {
                RadiographySubStage now = radioOf(piece);
                if (now == RadiographySubStage.SHOT) {
                    yield radioMove(RadiographySubStage.INTERPRETATION, dateRequired);
                }
                if (now == RadiographySubStage.INTERPRETATION) {
                    yield radioMove(RadiographySubStage.VT, dateRequired);
                }
                yield stageMove(
                        PieceStage.HEAT_TREATMENT,
                        null,
                        HeatTreatmentSubStage.AWAITING_OPERATIONS,
                        null,
                        dateRequired
                );
            }
            case HEAT_TREATMENT -> {
                HeatTreatmentSubStage now = heatOf(piece);
                yield switch (now) {
                    case AWAITING_OPERATIONS -> heatMove(HeatTreatmentSubStage.STAGE_1, dateRequired);
                    case STAGE_1 -> heatMove(HeatTreatmentSubStage.STAGE_2, dateRequired);
                    case STAGE_2 -> heatMove(HeatTreatmentSubStage.CNC_SAMPLE, dateRequired);
                    case CNC_SAMPLE, STAGE_3, STAGE_4, STAGE_5 ->
                            stageMove(PieceStage.TENSILE, null, null, null, dateRequired);
                };
            }
            case SHOT_BLASTING -> stageMove(PieceStage.READY, null, null, null, dateRequired);
            case READY -> stageMove(PieceStage.DELIVERED, null, null, null, dateRequired);
            case TENSILE, DELIVERED, WELDING, SCRAPPED -> null;
        };
    }

    private static ForwardMoveResponse stageMove(
            PieceStage stage,
            RadiographySubStage radio,
            HeatTreatmentSubStage heat,
            RoughMachiningSubStage rough,
            boolean processDateRequired
    ) {
        return new ForwardMoveResponse(
                ForwardMoveKind.STAGE,
                stage,
                radio,
                heat,
                rough,
                Permission.MOVE_PIECE_STAGE.name(),
                processDateRequired
        );
    }

    private static ForwardMoveResponse radioMove(RadiographySubStage target, boolean processDateRequired) {
        return new ForwardMoveResponse(
                ForwardMoveKind.RADIOGRAPHY_SUBSTAGE,
                PieceStage.RADIOGRAPHY,
                target,
                null,
                null,
                Permission.UPDATE_RADIOGRAPHY_SUBSTAGE.name(),
                processDateRequired
        );
    }

    private static ForwardMoveResponse heatMove(HeatTreatmentSubStage target, boolean processDateRequired) {
        return new ForwardMoveResponse(
                ForwardMoveKind.HEAT_TREATMENT_SUBSTAGE,
                PieceStage.HEAT_TREATMENT,
                null,
                target,
                null,
                Permission.UPDATE_HEAT_TREATMENT_SUBSTAGE.name(),
                processDateRequired
        );
    }

    private static ForwardMoveResponse roughMove(RoughMachiningSubStage target, boolean processDateRequired) {
        return new ForwardMoveResponse(
                ForwardMoveKind.ROUGH_MACHINING_SUBSTAGE,
                PieceStage.ROUGH_MACHINING,
                null,
                null,
                target,
                Permission.UPDATE_ROUGH_MACHINING_SUBSTAGE.name(),
                processDateRequired
        );
    }

    static RadiographySubStage radioOf(Piece piece) {
        return piece.getRadiographySubStage() != null
                ? piece.getRadiographySubStage()
                : RadiographySubStage.SHOT;
    }

    static HeatTreatmentSubStage heatOf(Piece piece) {
        return piece.getHeatTreatmentSubStage() != null
                ? piece.getHeatTreatmentSubStage()
                : HeatTreatmentSubStage.AWAITING_OPERATIONS;
    }

    static RoughMachiningSubStage roughOf(Piece piece) {
        return piece.getRoughMachiningSubStage() != null
                ? piece.getRoughMachiningSubStage()
                : RoughMachiningSubStage.DEBURRING;
    }
}
