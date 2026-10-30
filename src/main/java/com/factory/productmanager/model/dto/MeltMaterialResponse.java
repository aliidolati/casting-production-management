package com.factory.productmanager.model.dto;
import java.util.UUID;
public record MeltMaterialResponse(UUID id, String name, String category, boolean active) {}
