package com.factory.productmanager.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * یک عیب رادیوگرافی که اپراتور برای یک قطعه‌ی خاص ثبت می‌کند.
 * هر قطعه می‌تواند چند عیب در کورس‌ها و موقعیت‌های مختلف داشته باشد.
 * موقعیت به‌صورت دو سر متنیِ نقطه یا بازه ذخیره می‌شود، نه یک رشته ترکیبی.
 */
@Entity
@Table(name = "radiography_defects")
@Getter
@Setter
public class RadiographyDefect {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_id", nullable = false)
    @JsonBackReference
    private Piece piece;

    /**
     * سطری از گزارش رادیوگرافی که این عیب از داخل آن ثبت شده است.
     * برای عیوبی که مستقیماً از پنل عیوب قطعه ثبت می‌شوند null است.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_line_id")
    private RadiographyReportLine reportLine;

    @Column(nullable = false)
    private String course;

    // یکی از مقادیر ثابت فهرست عیب، یا OTHER وقتی کاربر نوع را خودش می‌نویسد
    @Column(name = "defect_type", nullable = false)
    private String defectType;

    /** فقط وقتی defectType برابر OTHER است: شرحی که کاربر نوشته */
    @Column(name = "defect_type_custom")
    private String defectTypeCustom;

    /** کلاس عیب، مثلاً T1 یا T3. ستون تاریخی type_code برای سازگاری جوشکاری حفظ شده است. */
    @Column(name = "type_code", nullable = false)
    private String defectClass;

    /**
     * موقعیت شروع متنی (مثلاً L4). نام ستون قدیمی برای سازگاری حفظ شده است.
     * location_cm برای نمایش سازگار ایستگاه جوش و داشبورد نگه داشته می‌شود.
     */
    @Column(name = "start_cm")
    private String startCm;

    /** موقعیت پایان. برای عیب نقطه‌ای با startCm برابر است. */
    @Column(name = "end_cm")
    private String endCm;

    /** موقعیت تکی قدیمی؛ هنگام ذخیره با startCm هم‌گام می‌شود */
    @Column(name = "location_cm")
    private String locationCm;

    /**
     * همه‌ی موقعیت‌های عیب اول (حداقل یکی برای زنجیره‌های جدید).
     * startCm/endCm/locationCm بالا برای سازگاری با ایستگاه جوش با اولین موقعیت هم‌گام می‌شوند.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "radiography_defect_positions", joinColumns = @JoinColumn(name = "defect_id"))
    @OrderColumn(name = "position_order")
    private List<DefectPosition> positions = new ArrayList<>();

    /**
     * فقط توصیفی: آیا برای عیب اول جوشکاری انجام شده است؟ (زنجیره‌ی گزارش: عیب → جوش → اقدام اصلاحی).
     * این مقدار روی جریان ایستگاه جوش اثری ندارد؛ آن جریان فقط با status = NEEDS_WELDING کار می‌کند.
     */
    @Column(name = "welded", nullable = false)
    private boolean welded = false;

    /** نام یکی از مقادیر CorrectiveAction؛ برای رکوردهای قدیمی null است */
    @Column(name = "corrective_action", length = 32)
    private String correctiveAction;

    /** نتیجه بررسی مجدد همان زنجیره؛ null در داده‌های قدیمی معادل false است. */
    @Column(name = "reshoot_ok")
    private Boolean reshootOk = false;

    /**
     * اگر این رکورد «عیب دوم» یک زنجیره باشد، به عیب اول همان زنجیره اشاره می‌کند.
     * unique یعنی هر عیب اول حداکثر یک عیب دوم دارد و عیب سوم از ساختار داده ممکن نیست.
     * عیب دوم موقعیت و جوشکاری ندارد.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "parent_defect_id",
            unique = true,
            // بدون کلید خارجی دیتابیس: حذف قطعه/پروژه همه‌ی عیب‌ها (اول و دوم) را با cascade پاک می‌کند
            // و ترتیب حذف دو ردیف هم‌جدول نباید باعث نقض constraint شود. یکتایی همچنان برقرار است.
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private RadiographyDefect parentDefect;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DefectStatus status;

    /**
     * فقط برای عیوب «نیاز به جوش» معنا دارد: بعد از انجام جوشکاری، اپراتور می‌تواند آن را تایید کند.
     * عیوب تاییدشده از هشدار داشبورد و شمارش «نیاز به جوش» در ردیابی حذف می‌شوند.
     */
    @Column(name = "welding_confirmed", nullable = false)
    private boolean weldingConfirmed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // نام کاربری که این عیب را ثبت کرده (برای رکوردهای قدیمی‌تر از این ویژگی، مقدارش null است)
    @Column(name = "recorded_by", updatable = false)
    private String recordedBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

}
