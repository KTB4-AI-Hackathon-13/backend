package hackathon.app.ai.plan.service;

import hackathon.app.ai.plan.dto.PlanTurnResponse;
import hackathon.app.conversation.ConversationService;
import hackathon.app.conversation.dto.request.MessageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** AI 호출이 성공한 턴의 메시지와 확정 계획을 하나의 트랜잭션으로 저장한다. */
@Service
@RequiredArgsConstructor
public class ConversationTurnPersistenceService {
    private final ConversationService conversationService;
    private final ConfirmedPlanPersistenceService planPersistenceService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Long persist(Long userId, String conversationId, MessageRequest request, PlanTurnResponse result) {
        conversationService.send(userId, conversationId, request.message(),
                objectMapper.writeValueAsString(request));
        conversationService.appendAssistant(userId, conversationId, result.assistant_message(),
                objectMapper.writeValueAsString(result),
                result.confirmed() ? AiPlanConversationRecorder.PLAN_CONFIRMED
                        : AiPlanConversationRecorder.PLAN_TURN);

        if (!result.confirmed()) {
            return null;
        }
        return planPersistenceService.save(
                userId, conversationId, request.goal_summary(), result.category(), result.plan());
    }
}
