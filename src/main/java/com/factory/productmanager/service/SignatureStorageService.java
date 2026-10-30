package com.factory.productmanager.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.UUID;

/**
 * ذخیره‌ی تصویر امضای کاربر و کپی تغییرناپذیر آن برای هر تأیید رادیوگرافی.
 *
 * <ul>
 *   <li>نوع فایل از روی «بایت‌های ابتدایی» تشخیص داده می‌شود (نه نام فایل یا Content-Type کلاینت)
 *       و فقط PNG و JPEG پذیرفته می‌شود؛ پس فایل اجرایی/SVG حاوی اسکریپت ذخیره نمی‌شود.</li>
 *   <li>نام فایل همیشه توسط سرور ساخته می‌شود؛ نام ارسالی کاربر هرگز در مسیر استفاده نمی‌شود.</li>
 *   <li>هر تأیید یک کپی مستقل از امضا نگه می‌دارد؛ تغییر یا حذف امضای پروفایل، تأییدهای قبلی را عوض نمی‌کند.</li>
 * </ul>
 */
@Service
public class SignatureStorageService {

    static final long MAX_BYTES = 512L * 1024;

    private final Path uploadRoot;

    public SignatureStorageService(@Value("${app.upload.dir:./uploads}") String uploadDir) throws IOException {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot.resolve("signatures"));
    }

    public record StoredSignature(String relativePath, String contentType) {
    }

    /** امضای پروفایل کاربر را اعتبارسنجی و با نام جدید ذخیره می‌کند (فایل قبلی را فراخواننده پاک می‌کند) */
    public StoredSignature storeProfileSignature(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("فایل امضا ارسال نشده است");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("حجم تصویر امضا نباید بیشتر از ۵۱۲ کیلوبایت باشد");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("خواندن فایل امضا با خطا مواجه شد", e);
        }
        String contentType = sniff(bytes);
        if (contentType == null) {
            throw new IllegalArgumentException("فقط تصویر PNG یا JPEG برای امضا مجاز است");
        }
        String relativePath = "signatures/profile-" + userId + "-" + UUID.randomUUID() + extensionFor(contentType);
        write(relativePath, bytes);
        return new StoredSignature(relativePath, contentType);
    }

    /** کپی مستقل امضا برای یک تأیید؛ اگر فایل مبدأ نباشد null برمی‌گرداند */
    public StoredSignature copyForApproval(String sourceRelativePath, String contentType) {
        Path source = resolve(sourceRelativePath);
        if (source == null || !Files.isRegularFile(source)) {
            return null;
        }
        String relativePath = "signatures/approval-" + UUID.randomUUID() + extensionFor(contentType);
        Path target = resolve(relativePath);
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("کپی امضا برای تأیید با خطا مواجه شد", e);
        }
        return new StoredSignature(relativePath, contentType);
    }

    /** تصویر به‌صورت data URL (برای نمایش و چاپ بدون نیاز به هدر احراز هویت)؛ در نبود فایل null */
    public String loadAsDataUrl(String relativePath, String contentType) {
        Path target = resolve(relativePath);
        if (target == null || !Files.isRegularFile(target)) {
            return null;
        }
        try {
            String type = contentType == null || contentType.isBlank() ? "image/png" : contentType;
            return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(target));
        } catch (IOException e) {
            return null;
        }
    }

    public boolean exists(String relativePath) {
        Path target = resolve(relativePath);
        return target != null && Files.isRegularFile(target);
    }

    public void deleteIfExists(String relativePath) {
        Path target = resolve(relativePath);
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // حذف فایل نباید کل عملیات دیتابیس را متوقف کند
        }
    }

    // ------------------------------------------------------------------

    private Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path target = uploadRoot.resolve(relativePath).normalize();
        return target.startsWith(uploadRoot) ? target : null;
    }

    private void write(String relativePath, byte[] bytes) {
        Path target = resolve(relativePath);
        if (target == null) {
            throw new IllegalStateException("مسیر ذخیره‌سازی نامعتبر است");
        }
        try {
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("ذخیره‌ی امضا با خطا مواجه شد", e);
        }
    }

    /** PNG یا JPEG بر اساس magic bytes؛ در غیر این صورت null */
    static String sniff(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }

    private static String extensionFor(String contentType) {
        return "image/jpeg".equalsIgnoreCase(contentType) ? ".jpg" : ".png";
    }
}
