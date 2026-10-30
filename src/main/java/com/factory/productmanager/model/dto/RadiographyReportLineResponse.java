package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * یک کورس کامل از جدول گزارش: مقادیر قالب به‌علاوه‌ی تفسیر و عیب‌های همان کورس.
 * شماره ردیف NO در این پاسخ نیست و در UI از روی ترتیب لیست ساخته می‌شود.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RadiographyReportLineResponse {
    private UUID id;
    private UUID templateLineId;
    private int sortOrder;
    private String courseCode;
    private String imageCount;
    private String iqiAbs;
    private String accCriteria;
    private String th;
    private String iqiReal;
    private String result;
    private List<RadiographyDefectResponse> defects;
}
