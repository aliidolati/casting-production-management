package com.factory.productmanager.service;

import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.AggregatedWeldingMapMarkResponse;
import com.factory.productmanager.model.dto.WeldingMapMarkRequest;
import com.factory.productmanager.model.dto.WeldingMapMarkResponse;
import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.PieceDefinition;
import com.factory.productmanager.model.entity.DefectTypes;
import com.factory.productmanager.model.entity.WeldingMapMark;
import com.factory.productmanager.repository.UserRepository;
import com.factory.productmanager.repository.WeldingMapMarkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class WeldingMapMarkService {

    private final WeldingMapMarkRepository weldingMapMarkRepository;
    private final PieceService pieceService;
    private final PieceDefinitionService pieceDefinitionService;
    private final UserRepository userRepository;

    public WeldingMapMarkResponse createMark(UUID pieceId, WeldingMapMarkRequest request, String username) {
        Piece piece = pieceService.findPieceOrThrow(pieceId);
        if (piece.getArchivedAt() != null) {
            throw new IllegalStateException("قطعه بایگانی‌شده قابل ویرایش نیست");
        }

        String drawingPath = piece.getPieceDefinition().getDrawingImagePath();
        if (drawingPath == null || drawingPath.isBlank()) {
            throw new IllegalStateException("برای این قطعه هنوز نقشه دو‌بعدی ثبت نشده است");
        }

        WeldingMapMark mark = new WeldingMapMark();
        mark.setPiece(piece);
        mark.setXPercent(request.getXPercent());
        mark.setYPercent(request.getYPercent());
        String storedType = DefectTypes.requireStoredType(request.getDefectType(), request.getDefectTypeCustom());
        mark.setDefectType(DefectTypes.displayLabel(
                storedType,
                DefectTypes.storedCustom(storedType, request.getDefectTypeCustom())
        ));
        mark.setNote(request.getNote() != null && !request.getNote().isBlank() ? request.getNote().trim() : null);
        mark.setCreatedBy(resolveDisplayName(username));

        return toResponse(weldingMapMarkRepository.save(mark));
    }

    @Transactional(readOnly = true)
    public List<WeldingMapMarkResponse> getMarksByPiece(UUID pieceId) {
        pieceService.findPieceOrThrow(pieceId);
        return weldingMapMarkRepository.findByPieceIdOrderByCreatedAtAsc(pieceId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * همه نقاط جوش همه شماره‌های یک تعریف قطعه (ردیف) را برای نمایش تجمیعی روی نقشه مشترک برمی‌گرداند.
     * اگر defectType داده شود، فقط همان نوع عیب فیلتر می‌شود.
     */
    @Transactional(readOnly = true)
    public List<AggregatedWeldingMapMarkResponse> getMarksByDefinition(UUID definitionId, String defectType) {
        PieceDefinition definition = pieceDefinitionService.findDefinitionOrThrow(definitionId);

        String drawingPath = definition.getDrawingImagePath();
        if (drawingPath == null || drawingPath.isBlank()) {
            throw new IllegalStateException("برای این تعریف قطعه هنوز نقشه دو‌بعدی ثبت نشده است");
        }

        String filter = defectType != null && !defectType.isBlank() ? defectType.trim() : null;

        return weldingMapMarkRepository.findByPieceDefinitionId(definitionId)
                .stream()
                .filter(m -> filter == null || filter.equalsIgnoreCase(m.getDefectType()))
                .map(this::toAggregatedResponse)
                .toList();
    }

    public void deleteMark(UUID id) {
        WeldingMapMark mark = weldingMapMarkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("نقطه جوشی با شناسه " + id + " پیدا نشد"));
        weldingMapMarkRepository.delete(mark);
    }

    private String resolveDisplayName(String username) {
        return userRepository.findByUsername(username)
                .map(u -> (u.getFullName() != null && !u.getFullName().isBlank()) ? u.getFullName() : u.getUsername())
                .orElse(username);
    }

    private WeldingMapMarkResponse toResponse(WeldingMapMark mark) {
        return new WeldingMapMarkResponse(
                mark.getId(),
                mark.getXPercent(),
                mark.getYPercent(),
                mark.getDefectType(),
                mark.getNote(),
                mark.getCreatedBy(),
                mark.getCreatedAt()
        );
    }

    private AggregatedWeldingMapMarkResponse toAggregatedResponse(WeldingMapMark mark) {
        return new AggregatedWeldingMapMarkResponse(
                mark.getId(),
                mark.getPiece().getId(),
                mark.getPiece().getPieceNumber(),
                mark.getXPercent(),
                mark.getYPercent(),
                mark.getDefectType(),
                mark.getNote(),
                mark.getCreatedBy(),
                mark.getCreatedAt()
        );
    }
}
