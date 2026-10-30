package com.factory.productmanager.model.entity;

import lombok.Getter;

/**
 * سایز کوره‌ای که سیکل عملیات حرارتی در آن انجام شده.
 * به‌جای متن آزاد enum است تا گزارش تجمیعی بر حسب کوره ممکن باشد.
 */
@Getter
public enum FurnaceSize {
    ONE_METER("۱ متری"),
    TWO_METER("۲ متری"),
    TWO_METER_C("۲ متری چ");

    private final String label;

    FurnaceSize(String label) {
        this.label = label;
    }
}
