package com.factory.productmanager.service;

import com.factory.productmanager.model.dto.DefectPositionDto;
import com.factory.productmanager.model.dto.RadiographyDefectResponse;
import com.factory.productmanager.model.dto.RadiographySecondDefectDto;
import com.factory.productmanager.model.entity.CorrectiveAction;
import com.factory.productmanager.model.entity.DefectPosition;
import com.factory.productmanager.model.entity.DefectStatus;
import com.factory.productmanager.model.entity.DefectTypes;
import com.factory.productmanager.model.entity.RadiographyDefect;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * تبدیل عیب رادیوگرافی (و عیب دوم زنجیره‌اش) به پاسخ API.
 * باید داخل تراکنش صدا زده شود چون positions به‌صورت lazy بارگذاری می‌شود.
 */
@Component
public class RadiographyDefectMapper {

    private static final String ARROW = " → ";

    /** secondDefect همان رکورد فرزندی است که parentDefect آن به defect اشاره می‌کند (یا null) */
    public RadiographyDefectResponse toResponse(RadiographyDefect defect, RadiographyDefect secondDefect) {
        List<DefectPositionDto> positions = positionsOf(defect);
        String start = defect.getStartCm() != null ? defect.getStartCm() : defect.getLocationCm();
        String end = defect.getEndCm() != null ? defect.getEndCm() : start;
        if (!positions.isEmpty()) {
            start = positions.get(0).getStartCm();
            end = positions.get(0).getEndCm();
        }
        String defectClass = defect.getDefectClass() == null ? "" : defect.getDefectClass();
        String actionLabel = CorrectiveAction.labelOf(defect.getCorrectiveAction());
        RadiographySecondDefectDto second = secondDefect == null ? null : toSecondDto(secondDefect);

        return new RadiographyDefectResponse(
                defect.getId(),
                defect.getReportLine() != null ? defect.getReportLine().getId() : null,
                defect.getCourse(),
                defect.getDefectType(),
                defect.getDefectTypeCustom(),
                defectClass,
                defectClass,
                start,
                end,
                start,
                defect.getStatus() == DefectStatus.NEEDS_WELDING,
                defect.getDescription(),
                defect.getStatus(),
                defect.isWeldingConfirmed(),
                defect.getCreatedAt(),
                defect.getRecordedBy(),
                positions,
                defect.isWelded(),
                defect.getCorrectiveAction(),
                actionLabel,
                second,
                Boolean.TRUE.equals(defect.getReshootOk()),
                chainText(positions, defect, second)
        );
    }

    private RadiographySecondDefectDto toSecondDto(RadiographyDefect second) {
        return new RadiographySecondDefectDto(
                second.getId(),
                second.getDefectType(),
                second.getDefectTypeCustom(),
                second.getDefectClass() == null ? "" : second.getDefectClass(),
                second.getCorrectiveAction(),
                CorrectiveAction.labelOf(second.getCorrectiveAction())
        );
    }

    /** موقعیت‌های ذخیره‌شده؛ برای رکوردهای قدیمی که هنوز positions ندارند از start/end/location ساخته می‌شود */
    private List<DefectPositionDto> positionsOf(RadiographyDefect defect) {
        List<DefectPositionDto> result = new ArrayList<>();
        for (DefectPosition p : defect.getPositions()) {
            result.add(new DefectPositionDto(p.getStartCm(), p.getEndCm()));
        }
        if (result.isEmpty()) {
            String start = defect.getStartCm() != null ? defect.getStartCm() : defect.getLocationCm();
            if (start != null) {
                String end = defect.getEndCm() != null ? defect.getEndCm() : start;
                result.add(new DefectPositionDto(start, end));
            }
        }
        return result;
    }

    /** (25) یا (12-20)؛ چند موقعیت پشت سر هم و بدون جداکننده: (12-20)(25)(30-35) */
    public static String positionsText(List<DefectPositionDto> positions) {
        StringBuilder sb = new StringBuilder();
        for (DefectPositionDto p : positions) {
            if (p.getStartCm() == null) {
                continue;
            }
            String end = p.getEndCm() == null ? p.getStartCm() : p.getEndCm();
            if (end.equals(p.getStartCm())) {
                sb.append('(').append(p.getStartCm()).append(')');
            } else {
                sb.append('(').append(p.getStartCm()).append('-').append(end).append(')');
            }
        }
        return sb.toString();
    }

    /**
     * (12-20)(25) F.M.L T1 → جوش → سنگ زنی → G.p T1 → OK
     * «جوش» فقط وقتی می‌آید که جوشکاری شده باشد؛ بخش‌های خالی حذف می‌شوند.
     */
    private String chainText(List<DefectPositionDto> positions, RadiographyDefect defect, RadiographySecondDefectDto second) {
        List<String> parts = new ArrayList<>();
        parts.add(join(positionsText(positions), typeAndClass(defect.getDefectType(), defect.getDefectTypeCustom(), defect.getDefectClass())));
        boolean reshoot = Boolean.TRUE.equals(defect.getReshootOk());
        if (defect.isWelded()) {
            parts.add(reshoot ? "ریشوت" : "جوش");
        }
        String action = CorrectiveAction.labelOf(defect.getCorrectiveAction());
        if (action != null) {
            parts.add(action);
        }
        if (reshoot) {
            parts.add(defect.isWelded() ? "جوش" : "ریشوت");
            parts.add("OK");
        } else if (second != null) {
            parts.add(typeAndClass(second.getDefectType(), second.getDefectTypeCustom(), second.getDefectClass()));
            if (second.getCorrectiveActionLabel() != null) {
                parts.add(second.getCorrectiveActionLabel());
            }
        }
        parts.removeIf(String::isBlank);
        return String.join(ARROW, parts);
    }

    private static String typeAndClass(String type, String custom, String defectClass) {
        String label = type == null ? "" : DefectTypes.displayLabel(type, custom);
        return join(label, defectClass == null ? "" : defectClass);
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) {
            return b == null ? "" : b.trim();
        }
        if (b == null || b.isBlank()) {
            return a.trim();
        }
        return a.trim() + " " + b.trim();
    }
}
