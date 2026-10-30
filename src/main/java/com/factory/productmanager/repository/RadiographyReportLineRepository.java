package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.RadiographyReportLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RadiographyReportLineRepository extends JpaRepository<RadiographyReportLine, UUID> {
}
