package com.factory.productmanager.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * ساخت یا ویرایش یک قالب. افزودن کورس جدید همیشه ممکن است؛ ویرایش و حذف کورس‌های
 * موجود فقط تا وقتی مجاز است که هنوز هیچ گزارشی روی این قالب ثبت نشده باشد.
 */
@Getter
@Setter
public class RadiographyTemplateRequest {
    @NotBlank(message = "نام قالب نمی‌تواند خالی باشد")
    @Size(max = 150, message = "نام قالب حداکثر می‌تواند ۱۵۰ کاراکتر باشد")
    private String name;

    @Valid
    @NotEmpty(message = "قالب باید حداقل یک لاین داشته باشد")
    private List<RadiographyTemplateLineRequest> lines = new ArrayList<>();
}
