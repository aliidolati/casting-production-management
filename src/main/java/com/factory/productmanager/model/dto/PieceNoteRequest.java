package com.factory.productmanager.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PieceNoteRequest {
    @NotBlank(message = "متن یادداشت نمی‌تواند خالی باشد")
    @Size(max = 4000, message = "متن یادداشت نمی‌تواند بیشتر از ۴۰۰۰ کاراکتر باشد")
    private String content;
}
