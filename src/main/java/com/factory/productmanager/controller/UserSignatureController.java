package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.RadiographyWorkflowDtos.SignatureStatusResponse;
import com.factory.productmanager.service.UserSignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** امضای پروفایل کاربر جاری؛ هیچ مسیری برای دسترسی به امضای کاربر دیگر وجود ندارد. */
@RestController
@RequestMapping("/api/profile/signature")
@RequiredArgsConstructor
public class UserSignatureController {

    private final UserSignatureService signatureService;

    @GetMapping
    public SignatureStatusResponse get(Authentication authentication) {
        return signatureService.get(authentication.getName());
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SignatureStatusResponse upload(@RequestParam("file") MultipartFile file, Authentication authentication) {
        return signatureService.upload(authentication.getName(), file);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication authentication) {
        signatureService.remove(authentication.getName());
    }
}