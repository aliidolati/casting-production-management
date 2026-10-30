package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false , nullable = false)
    private UUID id ;
    @Column(nullable = false, unique = true)
    private String code ;
    @Column(nullable = false)
    private String name ;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt ;
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PieceDefinition> pieceDefinitions = new ArrayList<>();
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

}
