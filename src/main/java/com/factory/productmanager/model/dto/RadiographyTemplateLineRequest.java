package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RadiographyTemplateLineRequest {
    /** هنگام ویرایش قالب: شناسه‌ی کورس موجود. برای کورس جدید خالی است. */
    private java.util.UUID id;

    @NotBlank(message = "LINE NO نمی‌تواند خالی باشد")
    @Size(max = 50, message = "LINE NO حداکثر ۵۰ کاراکتر است")
    private String courseCode;

    @Size(max = 50, message = "Number / image حداکثر ۵۰ کاراکتر است")
    private String imageCount;

    @Size(max = 50, message = "IQI ABS حداکثر ۵۰ کاراکتر است")
    private String iqiAbs;

    @Size(max = 50, message = "A.C.C Criteria حداکثر ۵۰ کاراکتر است")
    private String accCriteria;

    @Size(max = 50, message = "TH حداکثر ۵۰ کاراکتر است")
    private String th;
}
