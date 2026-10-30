package com.factory.productmanager.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PieceNoteResponse {
    private UUID id;
    private String content;
    private LocalDateTime createdAt;
    private String createdBy;
    // آیا کاربری که این درخواست را زده مجاز به حذف این یادداشت است؟ (خودِ نویسنده یا ادمین)
    private boolean canDelete;
}
