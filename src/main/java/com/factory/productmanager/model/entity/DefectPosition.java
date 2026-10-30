package com.factory.productmanager.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * یک موقعیت متنی عیب: نقطه‌ای (start == end) یا بازه‌ای، مثل L4 یا 12-20.
 * هر عیب اول می‌تواند چند موقعیت داشته باشد.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DefectPosition {
    @Column(name = "start_cm", nullable = false)
    private String startCm;

    @Column(name = "end_cm", nullable = false)
    private String endCm;
}
