package com.factory.productmanager.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {
    private LocalDateTime timestamp;
    private int status;
    private String message;
    private Map<String, String> errors;

    public ApiError(int status, String message) {
        this(LocalDateTime.now(), status, message, null);
    }

    public ApiError(int status, String message, Map<String, String> errors) {
        this(LocalDateTime.now(), status, message, errors);
    }
}
