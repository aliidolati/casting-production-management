package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.MeltPassportPieceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** All departures from CASTING, including admin and trusted routes, pass this gate. */
@Component
@RequiredArgsConstructor
public class MeltPassportGate {
    private final MeltPassportPieceRepository links;
    private final PermissionService permissions;

    public void assertManualDepartureAllowed(Piece piece, PieceStage target, String username) {
        if (piece.getCurrentStage() != PieceStage.CASTING || target == PieceStage.CASTING) return;
        if (!permissions.isManager(username)) {
            throw new IllegalStateException("انتقال دستی از ذوب‌ریزی فقط برای مدیر مجاز است");
        }
        links.findByActivePieceKey(piece.getId()).ifPresent(link -> {
            if (link.getPassport().getStatus() != MeltPassportStatus.APPROVED) {
                throw new IllegalStateException("ابتدا شناسنامه ذوب فعال را تأیید یا لغو کنید");
            }
        });
    }

    public void assertApprovedDeparture(Piece piece) {
        if (links.findByActivePieceKey(piece.getId())
                .filter(link -> link.getPassport().getStatus() == MeltPassportStatus.APPROVED).isEmpty()) {
            throw new IllegalStateException("خروج خودکار از ذوب‌ریزی نیاز به شناسنامه تأییدشده دارد");
        }
    }

    public void beginCastingCycle(Piece piece) {
        links.findByActivePieceKey(piece.getId()).ifPresent(link -> link.setActivePieceKey(null));
    }
}
