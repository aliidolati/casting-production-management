package com.factory.productmanager.service;

import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.RadiographyWorkflowDtos.SignatureStatusResponse;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * امضای پروفایل. فقط «خود کاربر احراز هویت‌شده» امضای خودش را می‌بیند/عوض می‌کند (نام کاربری از توکن می‌آید،
 * نه از پارامتر درخواست)، پس شناسه‌ی کاربر دیگر قابل دستکاری نیست. تأییدهای قبلی کپی مستقل دارند و با تغییر
 * یا حذف امضای پروفایل تغییر نمی‌کنند.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UserSignatureService {

    private final UserRepository userRepository;
    private final SignatureStorageService storage;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SignatureStatusResponse get(String username) {
        User user = load(username);
        if (user.getSignaturePath() == null) {
            return new SignatureStatusResponse(false, null, null);
        }
        String dataUrl = storage.loadAsDataUrl(user.getSignaturePath(), user.getSignatureContentType());
        return new SignatureStatusResponse(dataUrl != null, dataUrl, user.getSignatureUpdatedAt());
    }

    public SignatureStatusResponse upload(String username, MultipartFile file) {
        User user = load(username);
        SignatureStorageService.StoredSignature stored = storage.storeProfileSignature(user.getId(), file);
        String previous = user.getSignaturePath();
        user.setSignaturePath(stored.relativePath());
        user.setSignatureContentType(stored.contentType());
        user.setSignatureUpdatedAt(LocalDateTime.now(clock));
        userRepository.save(user);
        storage.deleteIfExists(previous);
        return get(username);
    }

    public void remove(String username) {
        User user = load(username);
        String previous = user.getSignaturePath();
        user.setSignaturePath(null);
        user.setSignatureContentType(null);
        user.setSignatureUpdatedAt(null);
        userRepository.save(user);
        storage.deleteIfExists(previous);
    }

    private User load(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر پیدا نشد"));
    }
}