package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.MeltMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MeltMaterialRepository extends JpaRepository<MeltMaterial, UUID> {
    Optional<MeltMaterial> findByNormalizedName(String name);
    List<MeltMaterial> findAllByOrderByNameAsc();
}
