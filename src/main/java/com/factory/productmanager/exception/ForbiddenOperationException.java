package com.factory.productmanager.exception;

/**
 * کاربر وارد شده و به منبع دسترسی دارد، ولی اجازه‌ی انجام «این عملیات» را ندارد.
 * پیام آن (برخلاف AccessDeniedException عمومی) به کاربر نمایش داده می‌شود،
 * مثلاً «شما اجازه‌ی تغییر این وظیفه را ندارید».
 */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
