package hackathon.app.ai.plan.service;

import hackathon.app.ai.plan.client.AiPlanClient;
import hackathon.app.ai.plan.dto.*;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.conversation.ConversationRepository;
import hackathon.app.domain.schedule.dto.ScheduleDetailResponse;
import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.schedule.service.ScheduleService;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 외부 AI 호출은 DB 트랜잭션 밖에서 수행하고, 응답을 받은 뒤 저장 서비스에 위임한다. */
@Service
@RequiredArgsConstructor
public class AiPlanService {
    private final AiPlanClient aiPlanClient;
    private final AiPlanPersistenceService persistenceService;
    private final ConversationRepository conversationRepository;
    private final ScheduleService scheduleService;
    private final ScheduleItemRepository itemRepository;

    public TemplateResponse generateTemplate(Long userId, GenerateTemplateRequest request) {
        validateConversation(userId, request.conversationId());
        TemplateResponse response = aiPlanClient.generateTemplate(
                new AiTemplatePayload(request.conversationId(), request.text()));
        if (response.action() == null || response.action().isBlank()) {
            throw new ApiException(ErrorCode.PLAN_INFORMATION_INCOMPLETE,
                    "AI 템플릿 응답에 action이 없습니다.");
        }
        if ("generate_template".equals(response.action())
                && (response.payload() == null || response.payload().questions() == null)) {
            throw new ApiException(ErrorCode.PLAN_INFORMATION_INCOMPLETE,
                    "AI 템플릿 응답에 질문 목록이 없습니다.");
        }
        return response;
    }

    public ScheduleDetailResponse generate(Long userId, GenerateScheduleRequest request) {
        validateConversation(userId, request.conversationId());
        AiPlanResult result = aiPlanClient.generate(new AiGeneratePayload(
                request.conversationId(), request.title(), request.categoryId()));
        return persistenceService.create(userId, request.title(), request.categoryId(), result);
    }

    public ScheduleDetailResponse revise(Long userId, Long scheduleId, ReviseScheduleRequest request) {
        validateConversation(userId, request.conversationId());
        Schedule schedule = scheduleService.getOwnedSchedule(userId, scheduleId);
        List<AiPlanTask> currentTasks = itemRepository
                .findBySchedule_IdOrderByScheduledDateAscPositionAscPriorityAscIdAsc(scheduleId).stream()
                .map(this::toAiTask).toList();
        AiPlanResult result = aiPlanClient.revise(new AiRevisePayload(scheduleId, request.conversationId(),
                request.instruction(), schedule.getDescription(), currentTasks));
        return persistenceService.revise(userId, scheduleId, result);
    }

    private void validateConversation(Long userId, String conversationId) {
        if (conversationRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(conversationId, userId).isEmpty()) {
            throw new ApiException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
    }

    private AiPlanTask toAiTask(ScheduleItem item) {
        return new AiPlanTask(item.getId(), item.getScheduledDate(), item.getTitle(),
                item.getDescription(), item.getEstimatedMinutes());
    }
}
