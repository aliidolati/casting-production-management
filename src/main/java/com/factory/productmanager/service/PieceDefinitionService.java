package com.factory.productmanager.service;

import com.factory.productmanager.exception.DuplicateResourceException;
import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.PieceDefinitionRequest;
import com.factory.productmanager.model.dto.PieceDefinitionResponse;
import com.factory.productmanager.model.dto.UpdatePieceDefinitionRequest;
import com.factory.productmanager.model.entity.PieceDefinition;
import com.factory.productmanager.model.entity.Project;
import com.factory.productmanager.repository.PieceDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PieceDefinitionService {
    private final PieceDefinitionRepository pieceDefinitionRepository;
    private final ProjectService projectService;
    private final DrawingStorageService drawingStorageService;

    public PieceDefinitionResponse createPieceDefinition(PieceDefinitionRequest request) {
        Project project = projectService.findProjectOrThrow(request.getProjectId());

        boolean exists = pieceDefinitionRepository.existsByProjectIdAndRowNum(
                request.getProjectId(),
                request.getRowNum()
        );

        if (exists) {
            throw new DuplicateResourceException(
                    "تعریف قطعه‌ای با ردیف " + request.getRowNum() +
                            " در این پروژه از قبل وجود دارد"
            );
        }
        PieceDefinition definition = new PieceDefinition();
        definition.setProject(project);
        definition.setRowNum(request.getRowNum());
        definition.setPieceName(request.getPieceName());
        definition.setMinUts(request.getMinUts());
        definition.setMinYs(request.getMinYs());
        definition.setMinEl(request.getMinEl());
        definition.setMinHb(request.getMinHb());

        PieceDefinition saved = pieceDefinitionRepository.save(definition);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PieceDefinitionResponse> getDefinitionsByProject(UUID projectId) {
        projectService.findProjectOrThrow(projectId);

        return pieceDefinitionRepository.findByProjectId(projectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PieceDefinitionResponse getDefinitionById(UUID id) {
        PieceDefinition definition = findDefinitionOrThrow(id);
        return toResponse(definition);
    }

    public PieceDefinitionResponse updateDefinition(UUID id, UpdatePieceDefinitionRequest request) {
        PieceDefinition definition = findDefinitionOrThrow(id);

        boolean exists = pieceDefinitionRepository.existsByProjectIdAndRowNumAndIdNot(
                definition.getProject().getId(),
                request.getRowNum(),
                id
        );

        if (exists) {
            throw new DuplicateResourceException(
                    "تعریف قطعه‌ای با ردیف " + request.getRowNum() +
                            " در این پروژه از قبل وجود دارد"
            );
        }

        definition.setRowNum(request.getRowNum());
        definition.setPieceName(request.getPieceName());
        definition.setMinUts(request.getMinUts());
        definition.setMinYs(request.getMinYs());
        definition.setMinEl(request.getMinEl());
        definition.setMinHb(request.getMinHb());

        PieceDefinition saved = pieceDefinitionRepository.save(definition);
        return toResponse(saved);
    }

    public PieceDefinitionResponse uploadDrawing(UUID id, MultipartFile file) {
        PieceDefinition definition = findDefinitionOrThrow(id);

        // اگر قبلاً فایلی با پسوند دیگر بوده، آن را پاک می‌کنیم تا orphan نماند
        drawingStorageService.deleteIfExists(definition.getDrawingImagePath());

        DrawingStorageService.StoredDrawing stored = drawingStorageService.storeDefinitionDrawing(id, file);
        definition.setDrawingImagePath(stored.relativePath());
        return toResponse(pieceDefinitionRepository.save(definition));
    }

    @Transactional(readOnly = true)
    public Resource loadDrawingResource(UUID id) {
        PieceDefinition definition = findDefinitionOrThrow(id);
        return drawingStorageService.loadAsResource(definition.getDrawingImagePath());
    }

    @Transactional(readOnly = true)
    public MediaType drawingMediaType(UUID id) {
        PieceDefinition definition = findDefinitionOrThrow(id);
        return drawingStorageService.mediaTypeFor(definition.getDrawingImagePath());
    }

    public PieceDefinitionResponse deleteDrawing(UUID id) {
        PieceDefinition definition = findDefinitionOrThrow(id);
        drawingStorageService.deleteIfExists(definition.getDrawingImagePath());
        definition.setDrawingImagePath(null);
        return toResponse(pieceDefinitionRepository.save(definition));
    }

    public void deleteDefinition(UUID id) {
        PieceDefinition definition = findDefinitionOrThrow(id);
        drawingStorageService.deleteIfExists(definition.getDrawingImagePath());
        pieceDefinitionRepository.delete(definition);
    }

    @Transactional(readOnly = true)
    public PieceDefinition findDefinitionOrThrow(UUID id) {
        return pieceDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "تعریف قطعه‌ای با شناسه " + id + " پیدا نشد"
                ));
    }

    private PieceDefinitionResponse toResponse(PieceDefinition definition) {
        boolean hasDrawing = definition.getDrawingImagePath() != null && !definition.getDrawingImagePath().isBlank();
        return new PieceDefinitionResponse(
                definition.getId(),
                definition.getProject().getId(),
                definition.getProject().getCode(),
                definition.getRowNum(),
                definition.getPieceName(),
                definition.getCreatedAt(),
                definition.getPieces().size(),
                hasDrawing,
                definition.getMinUts(),
                definition.getMinYs(),
                definition.getMinEl(),
                definition.getMinHb()
        );
    }
}

