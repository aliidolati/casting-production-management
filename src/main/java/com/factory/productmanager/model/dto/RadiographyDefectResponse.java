package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.DefectStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RadiographyDefectResponse {
    private UUID id;
    /** کورسی از گزارش که عیب از داخل آن ثبت شده؛ برای عیوب سطح قطعه null است */
    private UUID reportLineId;
    private String course;
    private String defectType;
    private String defectTypeCustom;
    private String defectClass;
    /** همان defectClass؛ برای ایستگاه جوش و نمایش‌های قدیمی */
    private String typeCode;
    private String startCm;
    private String endCm;
    /** همان startCm؛ برای نمایش‌هایی که هنوز موقعیت تکی می‌خوانند */
    private String locationCm;
    private boolean leadsToWelding;
    private String description;
    private DefectStatus status;
    private boolean weldingConfirmed;
    private LocalDateTime createdAt;
    private String recordedBy;

    /* ---- زنجیره‌ی عیب ---- */
    /** همه‌ی موقعیت‌های عیب اول به ترتیب ثبت */
    private List<DefectPositionDto> positions;
    /** جوشکاری شده؟ (توصیفی؛ جدا از جریان ایستگاه جوش) */
    private boolean welded;
    private String correctiveAction;
    private String correctiveActionLabel;
    /** عیب دوم؛ در حالت ریشوت یا نبود نتیجه بررسی مجدد null است. */
    private RadiographySecondDefectDto secondDefect;
    private boolean reshootOk;
    /** خروجی متنی زنجیره، مثل «(12-20)(25) F.M.L T1 → جوش → سنگ زنی» */
    private String chainText;
}
