package com.factory.productmanager.model.dto;

import com.factory.productmanager.model.entity.DefectStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * جزئیات یک عیب رادیوگرافی با وضعیت «نیاز به جوش»، برای نمایش در ویجت هشدار داشبورد.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NeedsWeldingDefectDetail {
    private UUID defectId;
    private String course;
    private String defectType;
    private String typeCode;
    private String locationCm;
    private DefectStatus status;
}
