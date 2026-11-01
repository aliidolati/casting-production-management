package com.factory.productmanager.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "pieces",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_row_piece",
                columnNames = {"piece_definition_id", "piece_number"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Piece {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // Prevent stale stage/archive writes from racing with atomic melt approval.
    @Version
    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "piece_definition_id", nullable = false)
    @JsonBackReference
    private PieceDefinition pieceDefinition;
    @Column(name = "piece_number", nullable = false)
    private Integer pieceNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false, length = 32)
    private PieceStage currentStage;
    // زیرمرحله‌ی فعلی داخل مرحله‌ی رادیوگرافی؛ فقط وقتی currentStage برابر RADIOGRAPHY است معنا دارد
    // (بعد از خروج از این مرحله هم به‌عنوان آخرین وضعیت ثبت‌شده نگه داشته می‌شود)
    @Convert(converter = RadiographySubStageConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "radiography_sub_stage", length = 32, columnDefinition = "varchar(32)")
    private RadiographySubStage radiographySubStage;
    // زیرمرحله‌ی فعلی داخل مرحله‌ی عملیات حرارتی؛ فقط وقتی currentStage برابر HEAT_TREATMENT است معنا دارد
    // (بعد از خروج از این مرحله هم به‌عنوان آخرین وضعیت ثبت‌شده نگه داشته می‌شود)
    @Enumerated(EnumType.STRING)
    @Column(name = "heat_treatment_sub_stage", length = 32)
    private HeatTreatmentSubStage heatTreatmentSubStage;
    // زیرمرحله‌ی فعلی داخل مرحله‌ی خشن‌تراشی؛ فقط وقتی currentStage برابر ROUGH_MACHINING است معنا دارد
    // (بعد از خروج از این مرحله هم به‌عنوان آخرین وضعیت ثبت‌شده نگه داشته می‌شود)
    @Enumerated(EnumType.STRING)
    @Column(name = "rough_machining_sub_stage", length = 32)
    private RoughMachiningSubStage roughMachiningSubStage;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    /** تاریخ واقعی انجام فرآیند فعلی؛ کاربر آن را دستی وارد می‌کند و مبنای گزارش مراحل است. */
    @Column(name = "process_date")
    private LocalDate processDate;
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
    @Column(name = "archived_by")
    private String archivedBy;
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "piece", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<PieceStageHistory> stageHistory = new ArrayList<>();

    @OneToMany(mappedBy = "piece", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<RadiographyDefect> radiographyDefects = new ArrayList<>();

    @OneToMany(mappedBy = "piece", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<PieceNote> notes = new ArrayList<>();

    @OneToMany(mappedBy = "piece", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<WeldingMapMark> weldingMapMarks = new ArrayList<>();

    @OneToMany(mappedBy = "piece", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<PieceSubStageHistory> subStageHistory = new ArrayList<>();

    @OneToMany(mappedBy = "piece", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("testIndex ASC")
    @JsonManagedReference
    private List<TensileTest> tensileTests = new ArrayList<>();

    @OneToMany(mappedBy = "piece", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<PieceWeldingDispatch> weldingDispatches = new ArrayList<>();

    @OneToMany(mappedBy = "piece", cascade = CascadeType.ALL, orphanRemoval = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<PieceScrapDispatch> scrapDispatches = new ArrayList<>();

    @OneToMany(mappedBy = "piece", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PieceStageSkip> stageSkips = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.currentStage == null) {
            this.currentStage = PieceStage.NOT_STARTED;
        }
    }
}
