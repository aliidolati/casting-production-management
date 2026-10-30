-- اگر V14 روی دیتابیس شرکت اجرا نشده باشد (یا Hibernate نوع ENUM را دست نزده باشد)،
-- مقدار جدید AWAITING_SHOT در ENUM قدیمی جا نمی‌شود و انتقال به رادیوگرافی
-- با خطای Data truncated for column 'radiography_sub_stage' شکست می‌خورد.
-- این ALTER روی ستون VARCHAR هم بی‌ضرر است و داده را پاک نمی‌کند.

ALTER TABLE pieces
    MODIFY COLUMN radiography_sub_stage VARCHAR(32) NULL;

ALTER TABLE piece_sub_stage_history
    MODIFY COLUMN kind VARCHAR(32) NOT NULL;
