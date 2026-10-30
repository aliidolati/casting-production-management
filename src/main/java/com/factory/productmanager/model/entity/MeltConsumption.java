package com.factory.productmanager.model.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity @Table(name="melt_consumptions", indexes={@Index(name="idx_melt_consumption_material",columnList="material_id"), @Index(name="idx_melt_consumption_passport",columnList="passport_id")}) @Getter @Setter
public class MeltConsumption {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;

 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="passport_id",nullable=false) private MeltPassport passport;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="material_id",nullable=false) private MeltMaterial material;
 private int position;
 @Column(nullable=false,length=160) private String materialName;
 @Column(nullable=false,length=100) private String category;
 @Column(precision=24,scale=8) private BigDecimal quantity;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16,columnDefinition="varchar(16)") private MeltMaterialUnit unit;
 @Column(precision=38,scale=12) private BigDecimal baseQuantity;
 @Column(nullable=false,length=16) private String baseUnit;
 private LocalTime addedAt;
 @Column(length=160) private String batchNumber;
 @Column(length=2000) private String notes;

}
