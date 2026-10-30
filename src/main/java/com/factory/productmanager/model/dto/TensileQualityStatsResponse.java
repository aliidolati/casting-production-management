package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class TensileQualityStatsResponse {
    private int plottedCount;
    private int greenCount;
    private int yellowCount;
    private int redCount;
    private int undefinedCount;
    private int unplottableCount;
    private BigDecimal averageQ;
    private BigDecimal averageYs;
    private BigDecimal averageEl;
}
