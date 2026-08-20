package hackathon.app.ai.plan.service;

import hackathon.app.ai.plan.client.AiPlanClient;
import hackathon.app.ai.plan.dto.AiRevisePayload;
import hackathon.app.ai.plan.dto.PlanTurnResponse;
import hackathon.app.ai.plan.dto.SchedulePlan;
import hackathon.app.conversation.ConversationService;
import hackathon.app.conversation.domain.Conversation;
import hackathon.app.conversation.dto.request.MessageRequest;
import hackathon.app.conversation.dto.response.ConversationTurnResponse;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConversationPlanService {
    private final ConversationService conversationService;
    private final AiPlanClient aiPlanClient;
    private final ConversationTurnPersistenceService turnPersistenceService;
    private final ScheduleItemRepository scheduleItems;

    public ConversationTurnResponse turn(Long userId, String conversationId, MessageRequest request) {
        // 권한/상태는 먼저 확인하되, AI 호출이 성공하기 전에는 메시지를 DB에 남기지 않는다.
        Conversation conversation = conversationService.requireActive(userId, conversationId);
        String requestedScheduleId = conversation.getScheduleId() == null
                ? null
                : conversation.getScheduleId().toString();
        SchedulePlan currentPlan = remainingPlan(conversation, request.current_plan());

        PlanTurnResponse result = aiPlanClient.revise(new AiRevisePayload(
                conversationId,
                requestedScheduleId,
                request.goal_summary(),
                request.category(),
                request.template_answers(),
                currentPlan,
                request.message(),
                request.feedback_history() == null ? List.of() : request.feedback_history(),
                request.busy_dates() == null ? List.of() : request.busy_dates()));

        // 확정 계획 저장이 실패하면 사용자/AI 메시지도 함께 롤백된다.
        Long savedScheduleId = turnPersistenceService.persist(userId, conversationId, request, result);
        Boolean submitted = result.confirmed() ? true : null;

        return new ConversationTurnResponse(
                result.assistant_message(), result.plan(), result.ready_to_confirm(), result.confirmed(),
                submitted, result.feedback_history(), savedScheduleId);
    }

    private SchedulePlan remainingPlan(Conversation conversation, SchedulePlan fallback) {
        if (conversation.getScheduleId() == null) return fallback;
        Set<String> editableTaskIds = scheduleItems
                .findBySchedule_IdOrderByScheduledDateAscPositionAscPriorityAscIdAsc(
                        conversation.getScheduleId())
                .stream()
                .filter(item -> item.getStatus() != ScheduleItemStatus.COMPLETED
                        && item.getStatus() != ScheduleItemStatus.CANCELLED)
                .map(item -> String.valueOf(item.getId()))
                .collect(java.util.stream.Collectors.toSet());
        var tasks = fallback.daily_tasks().stream()
                .filter(task -> task.id() == null || task.id().isBlank()
                        || editableTaskIds.contains(task.id()))
                .toList();
        return new SchedulePlan(fallback.summary(), tasks);
    }
}
