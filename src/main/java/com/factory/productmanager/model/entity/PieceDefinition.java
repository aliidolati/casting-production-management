package com.factory.productmanager.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@DynamicUpdate
@Table(
        name = "piece_definitions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_row_num",
                columnNames = {"project_id", "row_num"}
        )
)
@Getter
@Setter
public class PieceDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false ,nullable = false)
    private UUID id ;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @JsonBackReference
    private Project project;
    @Column(name = "row_num", nullable = false)
    private Integer rowNum ;
    @Column(name = "piece_name" , nullable = false)
    private String pieceName ;
    /** مسیر نسبی فایل نقشه دو‌بعدی روی دیسک (نسبت به app.upload.dir)؛ null یعنی هنوز آپلود نشده */
    @Column(name = "drawing_image_path")
    private String drawingImagePath;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // حداقل پارامترهای تاییدی تست کشش، مشترک بین همه‌ی قطعات این تعریف.
    // null یعنی برای آن پارامتر آستانه‌ای تعریف نشده و کنترل خودکار انجام نمی‌شود.
    @Column(name = "min_uts", precision = 10, scale = 2)
    private BigDecimal minUts;
    @Column(name = "min_ys", precision = 10, scale = 2)
    private BigDecimal minYs;
    @Column(name = "min_el", precision = 10, scale = 2)
    private BigDecimal minEl;
    @Column(name = "min_hb", precision = 10, scale = 2)
    private BigDecimal minHb;

    @Column(name = "radiography_shot_hours", precision = 10, scale = 2)
    private BigDecimal radiographyShotHours;
    @Column(name = "radiography_interpretation_hours", precision = 10, scale = 2)
    private BigDecimal radiographyInterpretationHours;

    @OneToMany(mappedBy = "pieceDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<Piece> pieces = new ArrayList<>();
    @OneToMany(mappedBy="definition",cascade=CascadeType.ALL,orphanRemoval=true)
    private List<DrawingRevision> drawingRevisions = new ArrayList<>();
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
