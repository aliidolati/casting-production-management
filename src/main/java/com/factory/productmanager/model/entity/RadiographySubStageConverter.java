package com.factory.productmanager.model.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * مقادیر قدیمی رادیوگرافی را هنگام خواندن به سه زیرمرحلهٔ فعلی نگاشت می‌کند
 * تا Hibernate با INTERPRETATION_FINALIZED و امثال آن از کار نیفتد.
 */
@Converter
public class RadiographySubStageConverter implements AttributeConverter<RadiographySubStage, String> {

    @Override
    public String convertToDatabaseColumn(RadiographySubStage attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public RadiographySubStage convertToEntityAttribute(String dbData) {
        return RadiographySubStage.fromStored(dbData);
    }
}
