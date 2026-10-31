package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity @Getter @Setter
@Table(name="piece_scrap_dispatches", indexes=@Index(name="idx_scrap_piece_sent", columnList="piece_id,sent_at"),
    uniqueConstraints=@UniqueConstraint(name="uk_scrap_active_piece", columnNames="active_piece_key"))
public class PieceScrapDispatch {
    @Id @GeneratedValue(strategy=GenerationType.UUID)
    private UUID id;
    @Version @Column(nullable=false, columnDefinition="bigint not null default 0")
    private long version;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="piece_id",nullable=false)
    private Piece piece;
    @Column(name="active_piece_key") private UUID activePieceKey;
    @Column(name="defect_type",nullable=false,length=255) private String defectType;
    @Column(name="defect_type_custom",length=255) private String defectTypeCustom;
    @Column(columnDefinition="TEXT") private String notes;
    // Nullable only for legacy scraps whose original time/actor was never recorded.
    @Column(name="sent_at") private LocalDateTime sentAt;
    @Column(name="sent_by") private String sentBy;
    @Enumerated(EnumType.STRING) @Column(name="origin_stage",length=32,columnDefinition="varchar(32)")
    private PieceStage originStage;
    @Column(name="origin_sub_stage",length=32) private String originSubStage;
    @Column(name="reversed_at") private LocalDateTime reversedAt;
    @Column(name="reversed_by") private String reversedBy;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @Column(name="updated_by") private String updatedBy;
    @ManyToMany
    @JoinTable(name="piece_scrap_dispatch_defects",joinColumns=@JoinColumn(name="dispatch_id"),
        inverseJoinColumns=@JoinColumn(name="defect_id"))
    private Set<RadiographyDefect> defects = new LinkedHashSet<>();
}
