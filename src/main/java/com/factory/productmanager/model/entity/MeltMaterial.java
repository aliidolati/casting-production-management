package com.factory.productmanager.model.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity @Table(name="melt_materials") @Getter @Setter
public class MeltMaterial {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;

 @Column(nullable=false,length=160) private String name;
 @Column(nullable=false,unique=true,length=160) private String normalizedName;
 @Column(nullable=false,length=100) private String category;
 @Column(nullable=false) private boolean active = true;

}
