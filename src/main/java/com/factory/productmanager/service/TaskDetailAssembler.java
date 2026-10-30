package com.factory.productmanager.service;

import com.factory.productmanager.model.dto.TaskDetailResponse;
import com.factory.productmanager.model.entity.Task;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.TaskCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** ساخت پاسخ جزئیات وظیفه (پیشرفت چک‌لیست + تعداد نظرها + مجوزهای بیننده) */
@Component
@RequiredArgsConstructor
public class TaskDetailAssembler {

    private final TaskMapper mapper;
    private final TaskChecklistService checklistService;
    private final TaskCommentRepository commentRepository;

    public TaskDetailResponse assemble(Task task, User viewer) {
        return mapper.toDetail(
                task,
                checklistService.progress(task.getId()),
                commentRepository.countByTaskId(task.getId()),
                viewer
        );
    }
}
