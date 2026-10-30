package com.factory.productmanager.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * تمام خطاهای پرتاب‌شده در لایه‌ی Controller/Service اینجا به یک پاسخ JSON
 * یکدست با کد وضعیت HTTP درست و پیام فارسی تبدیل می‌شوند.
 * بدون این کلاس، Spring Boot به‌طور پیش‌فرض همه‌ی خطاها را ۵۰۰ برمی‌گرداند
 * و فیلد message را هم در پاسخ نمی‌گذارد.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class,
            jakarta.persistence.OptimisticLockException.class,
            org.springframework.dao.PessimisticLockingFailureException.class,
            org.springframework.dao.DataIntegrityViolationException.class})
    public ResponseEntity<ApiError> handleConcurrentChange(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(409,
                "اطلاعات هم‌زمان تغییر کرده یا با رکورد موجود تداخل دارد؛ صفحه را تازه کنید و دوباره تلاش کنید"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiError(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError(HttpStatus.UNAUTHORIZED.value(), ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiError> handleForbiddenOperation(ForbiddenOperationException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ApiError(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    /** JSON خراب، مقدار نامعتبر برای enum یا تاریخ نامعتبر: ۴۰۰ با پیام قابل‌فهم، نه ۵۰۰ */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(),
                        "اطلاعات ارسالی معتبر نیست؛ مقدارها (مثل تاریخ یا وضعیت) را بررسی کنید"));
    }

    /** شناسه یا پارامتر کوئری با قالب نادرست (مثلاً UUID یا enum نامعتبر) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(),
                        "مقدار پارامتر «" + ex.getName() + "» معتبر نیست"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ApiError(HttpStatus.FORBIDDEN.value(), "شما اجازه‌ی دسترسی به این عملیات را ندارید"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), "اطلاعات ارسالی معتبر نیست", errors));
    }

    /**
     * فایل/مسیر ثابتِ ناموجود (مثلاً favicon.ico یا درخواست خودکار DevTools کروم به
     * /.well-known/appspecific/com.chrome.devtools.json). این‌ها خطای سرور نیستند؛ فقط ۴۰۴ برمی‌گردانیم
     * و stack trace در لاگ نمی‌نویسیم (قبلاً توسط handler عمومیِ پایین به‌صورت ERROR لاگ می‌شد).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiError(HttpStatus.NOT_FOUND.value(), "منبع موردنظر پیدا نشد"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("خطای پیش‌بینی‌نشده در پردازش درخواست", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "خطای داخلی سرور رخ داد"));
    }
}
