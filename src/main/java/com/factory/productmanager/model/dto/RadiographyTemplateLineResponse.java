package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RadiographyTemplateLineResponse {
    private UUID id;
    private int sortOrder;
    private String courseCode;
    private String imageCount;
    private String iqiAbs;
    private String accCriteria;
    private String th;
}
