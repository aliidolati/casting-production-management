package com.factory.productmanager.model.dto;
import java.util.UUID;
import java.math.BigDecimal;
public record MeltMaterialTotal(UUID materialId, String materialName, String baseUnit, BigDecimal quantity, long meltCount) {}
