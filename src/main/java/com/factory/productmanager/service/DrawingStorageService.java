package com.factory.productmanager.service;

import com.factory.productmanager.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * ذخیره و بازیابی فایل‌های نقشه دو‌بعدی تعریف قطعه روی دیسک محلی.
 */
@Service
public class DrawingStorageService {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final Path uploadRoot;

    public DrawingStorageService(@Value("${app.upload.dir:./uploads}") String uploadDir) throws IOException {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot.resolve("drawings"));
    }

    public record StoredDrawing(String relativePath, String contentType) {}

    public StoredDrawing storeDefinitionDrawing(UUID definitionId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("فایل نقشه ارسال نشده است");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("حجم فایل نباید بیشتر از ۱۰ مگابایت باشد");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("فقط فایل‌های JPG، PNG یا WEBP مجاز هستند");
        }

        String extension = extensionFor(contentType);
        String relativePath = "drawings/" + definitionId + extension;
        Path target = uploadRoot.resolve(relativePath).normalize();

        if (!target.startsWith(uploadRoot)) {
            throw new IllegalStateException("مسیر ذخیره‌سازی نامعتبر است");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("ذخیره فایل نقشه با خطا مواجه شد", e);
        }

        return new StoredDrawing(relativePath, contentType);
    }

    public void deleteIfExists(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        Path target = uploadRoot.resolve(relativePath).normalize();
        if (!target.startsWith(uploadRoot)) return;
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // حذف فایل نباید کل عملیات دیتابیس را متوقف کند
        }
    }

    public Resource loadAsResource(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new ResourceNotFoundException("نقشه‌ای برای این تعریف قطعه ثبت نشده است");
        }
        try {
            Path target = uploadRoot.resolve(relativePath).normalize();
            if (!target.startsWith(uploadRoot) || !Files.exists(target)) {
                throw new ResourceNotFoundException("فایل نقشه پیدا نشد");
            }
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("فایل نقشه قابل خواندن نیست");
            }
            return resource;
        } catch (IOException e) {
            throw new ResourceNotFoundException("فایل نقشه پیدا نشد");
        }
    }

    public MediaType mediaTypeFor(String relativePath) {
        String lower = relativePath == null ? "" : relativePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }

    private static String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
