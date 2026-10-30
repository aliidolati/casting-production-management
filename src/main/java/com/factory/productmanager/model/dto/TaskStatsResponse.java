package com.factory.productmanager.model.dto;

/**
 * آمار وظایف یک کاربر. عمداً هیچ «امتیاز عملکرد» ساخته نمی‌شود: دشواری وظایف متفاوت است
 * و نرخ تکمیل به‌تنهایی معیار کیفیت کاری نیست.
 *
 * completionRate = تکمیل‌شده ÷ (تکمیل‌شده + باز)، به درصد؛ وظایف لغو‌شده حساب نمی‌شوند.
 * اگر هنوز هیچ وظیفه‌ای نباشد null است (نه ۰٪).
 */
public record TaskStatsResponse(
        long openTasks,
        long inProgress,
        long waiting,
        long dueToday,
        long overdue,
        long completed,
        Integer completionRate
) {
}
