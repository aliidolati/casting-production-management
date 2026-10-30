package com.factory.productmanager.model.dto;

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
public class RadiographyTemplateResponse {
    private UUID id;
    private UUID pieceDefinitionId;
    private String name;
    private LocalDateTime createdAt;
    private String createdBy;
    private List<RadiographyTemplateLineResponse> lines;
    /** تعداد گزارش‌هایی که تا الان بر اساس این قالب صادر شده؛ برای هشدار هنگام ویرایش یا حذف */
    private long reportCount;
}
