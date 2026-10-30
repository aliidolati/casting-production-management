package com.factory.productmanager.model.dto;

import java.time.LocalDateTime;

/** dueDate برابر null یعنی مهلت حذف شود */
public record ChangeDeadlineRequest(LocalDateTime dueDate) {
}
