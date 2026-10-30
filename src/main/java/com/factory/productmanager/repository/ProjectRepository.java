package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);
}
