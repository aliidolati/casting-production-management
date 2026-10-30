package com.factory.productmanager.service;

import com.factory.productmanager.exception.DuplicateResourceException;
import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.ProjectRequest;
import com.factory.productmanager.model.dto.ProjectResponse;
import com.factory.productmanager.model.entity.Project;
import com.factory.productmanager.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectResponse createProject(ProjectRequest request) {
        if (projectRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException(
                    "پروژه‌ای با کد '" + request.getCode() + "' از قبل وجود دارد"
            );
        }
        Project project = new Project();
        project.setCode(request.getCode());
        project.setName(request.getName());

        Project saved = projectRepository.save(project);
        return toResponse(saved);
    }
    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(UUID id) {
        Project project = findProjectOrThrow(id);
        return toResponse(project);
    }
    public ProjectResponse updateProject(UUID id, ProjectRequest request) {
        Project project = findProjectOrThrow(id);

        if (projectRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new DuplicateResourceException(
                    "پروژه‌ای با کد '" + request.getCode() + "' از قبل وجود دارد"
            );
        }

        project.setCode(request.getCode());
        project.setName(request.getName());

        Project saved = projectRepository.save(project);
        return toResponse(saved);
    }

    public void deleteProject(UUID id) {
        Project project = findProjectOrThrow(id);
        projectRepository.delete(project);
    }

    @Transactional(readOnly = true)
    public Project findProjectOrThrow(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "پروژه‌ای با شناسه " + id + " پیدا نشد"
                ));
    }
    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getCode(),
                project.getName(),
                project.getCreatedAt(),
                project.getPieceDefinitions().size()
        );
    }

}
