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

    public void assertDepartureAllowed(Piece piece, PieceStage target) {
        if (piece.getCurrentStage() != PieceStage.CASTING || target == PieceStage.CASTING) return;
        var link = links.findByActivePieceKey(piece.getId());
        if (link.isEmpty() || link.get().getPassport().getStatus() != MeltPassportStatus.APPROVED) {
            throw new IllegalStateException("خروج از ذوب‌ریزی فقط پس از تأیید شناسنامه ذوب مجاز است");
        }
    }

    public void beginCastingCycle(Piece piece) {
        links.findByActivePieceKey(piece.getId()).ifPresent(link -> link.setActivePieceKey(null));
    }
}
