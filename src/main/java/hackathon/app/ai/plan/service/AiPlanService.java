package hackathon.app.ai.plan.service;

import hackathon.app.ai.plan.client.AiPlanClient;
import hackathon.app.ai.plan.dto.*;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.conversation.ConversationRepository;
import hackathon.app.conversation.ConversationService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 외부 AI 호출은 DB 트랜잭션 밖에서 수행하고, 응답을 받은 뒤 저장 서비스에 위임한다. */
@Service
@RequiredArgsConstructor
public class AiPlanService {
    private final AiPlanClient aiPlanClient;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;

    public TemplateResponse generateTemplate(Long userId, GenerateTemplateRequest request) {
        validateConversation(userId, request.conversationId());
        TemplateResponse response = aiPlanClient.generateTemplate(
                new AiTemplatePayload(request.text()));
        if (response.action() == null || response.action().isBlank()) {
            throw new ApiException(ErrorCode.PLAN_INFORMATION_INCOMPLETE,
                    "AI 템플릿 응답에 action이 없습니다.");
        }
        if ("generate_template".equals(response.action())
                && (response.payload() == null || response.payload().questions() == null)) {
            throw new ApiException(ErrorCode.PLAN_INFORMATION_INCOMPLETE,
                    "AI 템플릿 응답에 질문 목록이 없습니다.");
        }
        if (!"generate_template".equals(response.action()) && !"reject".equals(response.action())) {
            throw new ApiException(ErrorCode.PLAN_INFORMATION_INCOMPLETE,
                    "지원하지 않는 템플릿 action입니다: " + response.action());
        }
        conversationService.send(userId, request.conversationId(), request.text());
        conversationService.appendAssistant(userId, request.conversationId(), templateMessage(response));
        String title = response.payload() == null ? request.text() : response.payload().goal_summary();
        conversationService.rename(userId, request.conversationId(), title == null ? request.text() : title);
        return response;
    }

    public PlanTurnResponse generate(Long userId, GenerateScheduleRequest request) {
        validateConversation(userId, request.conversation_id());
        validateTemplateAnswers(request.template_answers());
        PlanTurnResponse response = validatePlanTurn(aiPlanClient.generate(new AiGeneratePayload(
                request.goal_summary(), request.category(), request.template_answers(),
                request.busy_dates() == null ? new Object[0] : request.busy_dates(),
                request.long_term_context())));
        conversationService.appendAssistant(
                userId, request.conversation_id(), response.assistant_message());
        return response;
    }

    private String templateMessage(TemplateResponse response) {
        if (response.payload() == null) return "계획 정보를 다시 입력해 주세요.";
        if (response.payload().message() != null && !response.payload().message().isBlank()) {
            return response.payload().message();
        }
        if (response.payload().goal_summary() != null && !response.payload().goal_summary().isBlank()) {
            return response.payload().goal_summary() + "\n\n계획을 만들기 위해 필요한 내용을 알려주세요.";
        }
        return "계획을 만들기 위해 필요한 내용을 알려주세요.";
    }

    private void validateConversation(Long userId, String conversationId) {
        if (conversationRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(conversationId, userId).isEmpty()) {
            throw new ApiException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
    }

    private void validateTemplateAnswers(Map<String, Object> answers) {
        if (answers == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "templateAnswers는 필수입니다.");
        }
        validateDateAnswer(answers, "start_date");
        validateDateAnswer(answers, "end_date");
    }

    private void validateDateAnswer(Map<String, Object> answers, String key) {
        Object value = answers.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "templateAnswers." + key + "는 필수입니다.");
        }
        try {
            LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "templateAnswers." + key + "는 YYYY-MM-DD 형식이어야 합니다.");
        }
    }

    private PlanTurnResponse validatePlanTurn(PlanTurnResponse response) {
        if (response.assistant_message() == null || response.assistant_message().isBlank()
                || response.plan() == null
                || response.feedback_history() == null) {
            throw new ApiException(ErrorCode.PLAN_INFORMATION_INCOMPLETE);
        }
        return response;
    }
}
