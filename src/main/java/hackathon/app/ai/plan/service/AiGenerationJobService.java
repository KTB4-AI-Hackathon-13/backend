package hackathon.app.ai.plan.service;

import hackathon.app.ai.plan.dto.AiGenerationAcceptedResponse;
import hackathon.app.ai.plan.dto.AiGenerationCreateRequest;
import hackathon.app.ai.plan.dto.AiGenerationStatusResponse;
import hackathon.app.ai.plan.dto.AiRevisionCreateRequest;
import hackathon.app.ai.plan.entity.AiGenerationJob;
import hackathon.app.ai.plan.entity.AiGenerationStatus;
import hackathon.app.ai.plan.entity.AiGenerationType;
import hackathon.app.ai.plan.repository.AiGenerationJobRepository;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.conversation.ConversationRepository;
import hackathon.app.domain.schedule.repository.ScheduleRepository;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import hackathon.app.domain.schedule.service.ScheduleService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiGenerationJobService {
    private static final List<AiGenerationStatus> IN_FLIGHT =
            List.of(AiGenerationStatus.PENDING, AiGenerationStatus.RUNNING);
    private final AiGenerationJobRepository jobs;
    private final ConversationRepository conversations;
    private final ScheduleRepository schedules;
    private final ScheduleItemRepository items;
    private final ScheduleService scheduleService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public AiGenerationAcceptedResponse create(Long userId, AiGenerationCreateRequest request) {
        requireConversation(userId, request.conversationId());
        rejectDuplicate(userId, request.conversationId());
        AiGenerationJob job = jobs.save(AiGenerationJob.create(userId, request.conversationId(), null,
                request.categoryId(), request.title(), null, AiGenerationType.GENERATE, LocalDateTime.now(clock)));
        events.publishEvent(new AiGenerationRequested(job.getId()));
        return new AiGenerationAcceptedResponse(job.getId(), job.getStatus());
    }

    @Transactional
    public AiGenerationAcceptedResponse revise(Long userId, Long scheduleId, AiRevisionCreateRequest request) {
        requireConversation(userId, request.conversationId());
        scheduleService.getOwnedSchedule(userId, scheduleId);
        rejectDuplicate(userId, request.conversationId());
        AiGenerationJob job = jobs.save(AiGenerationJob.create(userId, request.conversationId(), scheduleId,
                null, null, request.instruction(), AiGenerationType.REVISE, LocalDateTime.now(clock)));
        events.publishEvent(new AiGenerationRequested(job.getId()));
        return new AiGenerationAcceptedResponse(job.getId(), job.getStatus());
    }

    @Transactional(readOnly = true)
    public AiGenerationStatusResponse get(Long userId, String generationId) {
        AiGenerationJob job = jobs.findByIdAndUserId(generationId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.GENERATION_NOT_FOUND));
        return AiGenerationStatusResponse.of(job, job.getScheduleId() == null ? List.of()
                : items.findBySchedule_IdOrderByScheduledDateAscPositionAscPriorityAscIdAsc(job.getScheduleId()));
    }

    private void requireConversation(Long userId, String conversationId) {
        if (conversations.findByIdAndOwnerUserIdAndDeletedAtIsNull(conversationId, userId).isEmpty()) {
            throw new ApiException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
    }

    private void rejectDuplicate(Long userId, String conversationId) {
        if (jobs.existsByUserIdAndConversationIdAndStatusIn(userId, conversationId, IN_FLIGHT)) {
            throw new ApiException(ErrorCode.GENERATION_ALREADY_RUNNING);
        }
    }
}
