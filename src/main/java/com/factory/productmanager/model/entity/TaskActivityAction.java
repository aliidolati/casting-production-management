package com.factory.productmanager.model.entity;

/**
 * نوع رویدادهای ثبت‌شده در تاریخچه‌ی وظیفه. هر رویداد ساخت‌یافته است
 * (action + oldValue + newValue) تا بعداً برای تحلیل (مثلاً میانگین زمان در هر وضعیت) قابل استفاده باشد.
 * رویدادهای مربوط به وضعیت (STATUS_CHANGED / COMPLETED / REOPENED / CANCELLED) همیشه
 * وضعیت قبلی و جدید را در oldValue/newValue دارند.
 */
public enum TaskActivityAction {
    CREATED,
    ASSIGNED,
    ASSIGNEE_CHANGED,
    TITLE_CHANGED,
    DESCRIPTION_CHANGED,
    PRIORITY_CHANGED,
    DEADLINE_CHANGED,
    PROJECT_CHANGED,
    PART_CHANGED,
    STATUS_CHANGED,
    COMPLETED,
    REOPENED,
    CANCELLED,
    COMMENT_ADDED,
    COMMENT_DELETED,
    CHECKLIST_ITEM_ADDED,
    CHECKLIST_ITEM_COMPLETED,
    CHECKLIST_ITEM_REOPENED,
    CHECKLIST_ITEM_REMOVED
}
