package com.factory.productmanager.model.dto;

import java.util.UUID;

/** مرجع سبک یک قطعه (Piece) همراه با پروژه‌ی آن، برای نمایش و ناوبری */
public record PartRefResponse(
        UUID id,
        Integer pieceNumber,
        String pieceName,
        Integer rowNum,
        UUID projectId,
        String projectCode,
        String label
) {
}
