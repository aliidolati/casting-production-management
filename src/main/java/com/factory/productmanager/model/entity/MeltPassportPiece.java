package com.factory.productmanager.model.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Entity @Table(name="melt_passport_pieces", uniqueConstraints=@UniqueConstraint(name="uk_melt_passport_piece",columnNames={"passport_id","piece_id"}), indexes={@Index(name="idx_melt_link_piece",columnList="piece_id"), @Index(name="idx_melt_link_passport",columnList="passport_id")}) @Getter @Setter
public class MeltPassportPiece {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;

 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="passport_id",nullable=false) private MeltPassport passport;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="piece_id",nullable=false) private Piece piece;
 // Null on a new casting cycle; old memberships remain in history.
 @Column(unique=true) private UUID activePieceKey;
 @Column(nullable=false,length=255) private String pieceName;
 @Column(nullable=false,length=255) private String pieceRef;

}
