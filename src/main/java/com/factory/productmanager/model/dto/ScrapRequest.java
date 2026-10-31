package com.factory.productmanager.model.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Conditional on a move to SCRAPPED. Validated again at the central stage boundary. */
@Getter @Setter
public class ScrapRequest {
    private String defectType;
    private String defectTypeCustom;
    private String notes;
    private List<UUID> defectIds = new ArrayList<>();
    /** Required for editing an existing dispatch; null only when filling a legacy missing reason. */
    private Long version;
}
