package com.factory.productmanager.config;

import com.factory.productmanager.model.entity.RadiographySubStage;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * پارامترهای API و فرم‌ها را هم با نام‌های قدیمی رادیوگرافی می‌پذیرد.
 */
@Component
public class RadiographySubStageFormatter implements Converter<String, RadiographySubStage> {

    @Override
    public RadiographySubStage convert(String source) {
        return RadiographySubStage.fromStored(source);
    }
}
