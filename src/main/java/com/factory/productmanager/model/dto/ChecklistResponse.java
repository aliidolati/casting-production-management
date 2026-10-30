package com.factory.productmanager.model.dto;

import java.util.List;

public record ChecklistResponse(List<ChecklistItemResponse> items, ChecklistProgressResponse progress) {
}
