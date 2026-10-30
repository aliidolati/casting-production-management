package com.factory.productmanager.service;

import com.factory.productmanager.exception.ForbiddenOperationException;
import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.TaskCommentRequest;
import com.factory.productmanager.model.dto.TaskCommentResponse;
import com.factory.productmanager.model.entity.Task;
import com.factory.productmanager.model.entity.TaskActivityAction;
import com.factory.productmanager.model.entity.TaskComment;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.TaskCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * نظرهای وظیفه. هر کسی که حق دیدن وظیفه را دارد می‌تواند نظر بگذارد (حتی روی وظیفه‌ی بسته‌شده،
 * مثلاً برای پیگیری). ویرایش فقط برای نویسنده‌ی نظر است؛ حذف برای نویسنده یا مدیر وظیفه.
 * متن نظر به‌صورت متن ساده ذخیره می‌شود و فرانت‌اند آن را escape می‌کند.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TaskCommentService {

    private static final int EXCERPT_LENGTH = 100;
    private static final String COMMENT_NOT_FOUND = "نظر پیدا نشد";

    private final TaskCommentRepository commentRepository;
    private final TaskLookup lookup;
    private final TaskAccessPolicy policy;
    private final TaskActivityService activityService;
    private final TaskNotificationService notificationService;
    private final TaskMapper mapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TaskCommentResponse> list(UUID taskId, String username) {
        User actor = lookup.actor(username);
        Task task = lookup.visibleTask(taskId, actor);
        boolean moderator = policy.canManage(actor, task);
        return commentRepository.findByTaskIdOrderByCreatedAtAscIdAsc(task.getId()).stream()
                .map(comment -> mapper.toComment(comment, actor, moderator))
                .toList();
    }

    public TaskCommentResponse add(UUID taskId, TaskCommentRequest request, String username) {
        User actor = lookup.actor(username);
        Task task = lookup.visibleTask(taskId, actor);

        OperationClock op = new OperationClock(clock);
        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setAuthor(actor);
        comment.setContent(request.content().trim());
        comment.setCreatedAt(op.base());
        comment.setUpdatedAt(op.base());
        comment = commentRepository.save(comment);

        activityService.record(task, actor, TaskActivityAction.COMMENT_ADDED,
                null, excerpt(comment.getContent()), null, op.tick());
        notificationService.onComment(task, actor);
        return mapper.toComment(comment, actor, policy.canManage(actor, task));
    }

    public TaskCommentResponse edit(UUID taskId, UUID commentId, TaskCommentRequest request, String username) {
        User actor = lookup.actor(username);
        Task task = lookup.visibleTask(taskId, actor);
        TaskComment comment = findComment(taskId, commentId);

        if (!comment.getAuthor().getId().equals(actor.getId())) {
            throw new ForbiddenOperationException("فقط نویسنده‌ی نظر می‌تواند آن را ویرایش کند");
        }
        String newContent = request.content().trim();
        if (!newContent.equals(comment.getContent())) {
            comment.setContent(newContent);
            comment.setUpdatedAt(new OperationClock(clock).base());
        }
        return mapper.toComment(commentRepository.save(comment), actor, policy.canManage(actor, task));
    }

    public void delete(UUID taskId, UUID commentId, String username) {
        User actor = lookup.actor(username);
        Task task = lookup.visibleTask(taskId, actor);
        TaskComment comment = findComment(taskId, commentId);

        boolean own = comment.getAuthor().getId().equals(actor.getId());
        if (!own && !policy.canManage(actor, task)) {
            throw new ForbiddenOperationException("شما اجازه‌ی حذف این نظر را ندارید");
        }
        activityService.record(task, actor, TaskActivityAction.COMMENT_DELETED,
                excerpt(comment.getContent()), null, null, new OperationClock(clock).tick());
        commentRepository.delete(comment);
    }

    /** نظر باید واقعاً متعلق به همین وظیفه باشد (جلوگیری از دسترسی به نظر وظیفه‌ی دیگر با شناسه‌ی حدسی) */
    private TaskComment findComment(UUID taskId, UUID commentId) {
        return commentRepository.findByIdAndTaskId(commentId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException(COMMENT_NOT_FOUND));
    }

    private static String excerpt(String content) {
        String singleLine = content.replaceAll("\\s+", " ");
        return singleLine.length() <= EXCERPT_LENGTH ? singleLine : singleLine.substring(0, EXCERPT_LENGTH - 1) + "…";
    }
}
