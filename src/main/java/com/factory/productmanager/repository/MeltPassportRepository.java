package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.MeltPassport;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface MeltPassportRepository extends JpaRepository<MeltPassport, UUID>, JpaSpecificationExecutor<MeltPassport> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MeltPassport m where m.id = :id")
    Optional<MeltPassport> lockById(@Param("id") UUID id);

    @Query("select distinct m.ingotType from MeltPassport m where m.ingotType is not null order by m.ingotType")
    List<String> ingotTypes();
}
