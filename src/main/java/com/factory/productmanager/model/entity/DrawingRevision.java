package com.factory.productmanager.model.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name="drawing_revisions",uniqueConstraints=@UniqueConstraint(name="uk_drawing_revision_number",columnNames={"definition_id","revision_number"})) @Getter @Setter
public class DrawingRevision {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="definition_id",nullable=false) private PieceDefinition definition;
 @Column(name="revision_number",nullable=false) private int revisionNumber;
 @Column(name="relative_path",nullable=false) private String relativePath;
 @Column(name="original_name") private String originalName;
 @Column(name="uploaded_at") private LocalDateTime uploadedAt;
 @Column(name="captured_at",nullable=false) private LocalDateTime capturedAt;
 @Column(name="uploaded_by") private String uploadedBy;
 @Column(name="source_revision_id") private UUID sourceRevisionId;
}
