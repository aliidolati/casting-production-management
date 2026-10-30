-- V12 باید notes را nullable کرده باشد؛ اگر آن ALTER روی دیتابیس اعمال نشده،
-- ارسال به جوشکاری بدون توضیح با خطای Column 'notes' cannot be null شکست می‌خورد.

ALTER TABLE piece_welding_dispatches
    MODIFY COLUMN notes TEXT NULL;
