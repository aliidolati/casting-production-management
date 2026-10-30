package com.factory.productmanager.model.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity @Table(name="melt_checks", uniqueConstraints=@UniqueConstraint(name="uk_melt_check_kind",columnNames={"passport_id","kind"}), indexes={@Index(name="idx_melt_check_passport",columnList="passport_id")}) @Getter @Setter
public class MeltCheck {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;

 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="passport_id",nullable=false) private MeltPassport passport;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32,columnDefinition="varchar(32)") private MeltCheckKind kind;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16,columnDefinition="varchar(16)") private MeltCheckStatus status = MeltCheckStatus.UNCHECKED;
 @Column(length=2000) private String notes;

}
