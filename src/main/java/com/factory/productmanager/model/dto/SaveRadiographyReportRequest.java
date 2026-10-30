package com.factory.productmanager.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ذخیره‌ی گزارش یک قطعه. اگر گزارشی برای این قطعه و قالب وجود داشته باشد
 * همان ویرایش می‌شود، وگرنه ساخته می‌شود.
 */
@Getter
@Setter
public class SaveRadiographyReportRequest {
    @NotNull(message = "انتخاب قالب گزارش الزامی است")
    private UUID templateId;

    private LocalDate reportDate;

    @Size(max = 80, message = "شماره گزارش حداکثر ۸۰ کاراکتر است")
    private String reportNumber;

    @Size(max = 80, message = "شماره درخواست حداکثر ۸۰ کاراکتر است")
    private String requestNumber;

    @Size(max = 255, message = "استاندارد مرجع حداکثر ۲۵۵ کاراکتر است")
    private String referenceStandard;

    /** DRAFT، IN_INTERPRETATION یا COMPLETED. خالی یعنی سرویس از روی داده‌ها تعیین کند. */
    @Size(max = 32, message = "وضعیت گزارش معتبر نیست")
    private String status;

    @Valid
    private List<RadiographyReportLineRequest> lines = new ArrayList<>();
}
