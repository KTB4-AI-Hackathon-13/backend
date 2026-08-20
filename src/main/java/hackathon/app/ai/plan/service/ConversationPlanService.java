package hackathon.app.ai.plan.service;

import hackathon.app.ai.plan.client.AiPlanClient;
import hackathon.app.ai.plan.dto.AiRevisePayload;
import hackathon.app.ai.plan.dto.PlanTurnResponse;
import hackathon.app.conversation.ConversationService;
import hackathon.app.conversation.domain.Conversation;
import hackathon.app.conversation.dto.request.MessageRequest;
import hackathon.app.conversation.dto.response.ConversationTurnResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConversationPlanService {
    private final ConversationService conversationService;
    private final AiPlanClient aiPlanClient;
    private final ConversationTurnPersistenceService turnPersistenceService;

    public ConversationTurnResponse turn(Long userId, String conversationId, MessageRequest request) {
        // 권한/상태는 먼저 확인하되, AI 호출이 성공하기 전에는 메시지를 DB에 남기지 않는다.
        Conversation conversation = conversationService.requireActive(userId, conversationId);
        String requestedScheduleId = conversation.getScheduleId() == null
                ? null
                : conversation.getScheduleId().toString();

        PlanTurnResponse result = aiPlanClient.revise(new AiRevisePayload(
                conversationId,
                requestedScheduleId,
                request.goal_summary(),
                request.category(),
                request.template_answers(),
                request.current_plan(),
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
}
