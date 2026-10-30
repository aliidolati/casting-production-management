package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class SendToWeldingRequest {
    /** ربط اختیاری به عیب رادیوگرافی موجود؛ برای ارسال به جوشکاری الزامی نیست. */
    private List<UUID> defectIds = new ArrayList<>();

    @NotBlank(message = "نوع عیب را انتخاب کنید")
    private String defectType;

    private String defectTypeCustom;

    private String notes;
}
