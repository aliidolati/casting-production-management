package com.factory.productmanager.model.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity @Table(name="melt_passports", indexes={@Index(name="idx_melt_status_created",columnList="status,createdAt"), @Index(name="idx_melt_date_furnace",columnList="meltDate,furnaceNumber")}) @Getter @Setter
public class MeltPassport {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;

 @Version private long version;
 private UUID assignedOperatorId;
 @Column(length=255) private String assignedOperatorName;
 @Column(nullable=false,unique=true,length=48) private String number;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32,columnDefinition="varchar(32)") private MeltPassportStatus status = MeltPassportStatus.DRAFT;
 private LocalDate meltDate;
 private Integer furnaceNumber;
 @Column(length=160) private String ingotType;
 @Column(length=160) private String primaryBatch;
 @Column(length=160) private String recycledBatch;
 @Column(precision=24,scale=8) private BigDecimal primaryWeight;
 @Column(precision=24,scale=8) private BigDecimal recycledWeight;
 @Column(length=160) private String injectionOperator;
 @Column(length=4000) private String notes;
 @Column(length=4000) private String additionalNotes;
 @Embedded private MeltProcess process = new MeltProcess();
 @Column(precision=24,scale=8) private BigDecimal mg;
 @Column(precision=24,scale=8) private BigDecimal mgRotary;
 @Column(precision=24,scale=8) private BigDecimal mg610;
 @Column(precision=24,scale=8) private BigDecimal mgAr;
 @Column(precision=24,scale=8) private BigDecimal mgB;
 @Column(precision=24,scale=8) private BigDecimal mgW;
 @Column(precision=38,scale=12) private BigDecimal magnesiumResult;
 private LocalDateTime magnesiumCalculatedAt;
 private LocalDateTime createdAt;
 private LocalDateTime updatedAt;
 private UUID createdById;
 @Column(length=255) private String createdBy;
 private UUID updatedById;
 @Column(length=255) private String updatedBy;
 private LocalDateTime submittedAt;
 private UUID submittedById;
 @Column(length=255) private String submittedBy;
 private LocalDateTime approvedAt;
 private UUID approvedById;
 @Column(length=255) private String approvedBy;
 private LocalDateTime returnedAt;
 private UUID returnedById;
 @Column(length=255) private String returnedBy;
 @Column(length=2000) private String correctionReason;
 @OneToMany(mappedBy="passport",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("pieceRef ASC")
 private List<MeltPassportPiece> pieces = new ArrayList<>();
 @OneToMany(mappedBy="passport",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("position ASC")
 private List<MeltConsumption> materials = new ArrayList<>();
 @OneToMany(mappedBy="passport",cascade=CascadeType.ALL,orphanRemoval=true)
 private List<MeltCheck> checks = new ArrayList<>();
 @OneToMany(mappedBy="passport",cascade=CascadeType.ALL) @OrderBy("occurredAt ASC")
 private List<MeltPassportEvent> events = new ArrayList<>();

}
