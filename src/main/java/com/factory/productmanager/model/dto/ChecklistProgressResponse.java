package com.factory.productmanager.model.dto;

/** پیشرفت چک‌لیست؛ percent بین ۰ تا ۱۰۰ (برای چک‌لیست خالی ۰) */
public record ChecklistProgressResponse(int total, int completed, int percent) {

    public static ChecklistProgressResponse of(long total, long completed) {
        int percent = total <= 0 ? 0 : (int) Math.round(completed * 100.0 / total);
        return new ChecklistProgressResponse((int) total, (int) completed, percent);
    }
}
