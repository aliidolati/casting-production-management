package com.factory.productmanager.model.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * قالب یکنواخت پاسخ‌های صفحه‌بندی‌شده (page از ۰ شروع می‌شود).
 * ترتیب پارامترهای سازنده با استفاده‌ی قبلی در PieceService یکی است.
 */
public record PageResponse<T>(List<T> items, long totalElements, int page, int size, int totalPages) {

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return of(page, page.getContent().stream().map(mapper).toList());
    }

    public static <T> PageResponse<T> of(Page<?> page, List<T> items) {
        return new PageResponse<>(items, page.getTotalElements(), page.getNumber(), page.getSize(), page.getTotalPages());
    }
}
