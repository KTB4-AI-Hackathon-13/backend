package hackathon.app.ai.plan.service;

import hackathon.app.ai.plan.client.AiPlanClient;
import hackathon.app.ai.plan.dto.*;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.conversation.ConversationRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import hackathon.app.image.service.ImageService;

/** 외부 AI 호출은 DB 트랜잭션 밖에서 수행하고, 응답을 받은 뒤 저장 서비스에 위임한다. */
@Service
@RequiredArgsConstructor
public class AiPlanService {
    private final AiPlanClient aiPlanClient;
    private final ConversationRepository conversationRepository;
    private final AiPlanConversationRecorder conversationRecorder;
    private final ConfirmedPlanPersistenceService confirmedPlanPersistenceService;
    private final ImageService imageService;

    public TemplateResponse generateTemplate(Long userId, GenerateTemplateRequest request) {
        validateConversation(userId, request.conversationId());
        TemplateResponse response = aiPlanClient.generateTemplate(
                new AiTemplatePayload(request.message()));
        if (response.action() == null || response.action().isBlank()) {
            throw new ApiException(ErrorCode.PLAN_INFORMATION_INCOMPLETE,
                    "AI 템플릿 응답에 action이 없습니다.");
        }
        if ("generate_template".equals(response.action())
                && (response.payload() == null || response.payload().questions() == null)) {
            throw new ApiException(ErrorCode.PLAN_INFORMATION_INCOMPLETE,
                    "AI 템플릿 응답에 질문 목록이 없습니다.");
        }
        String action = switch (response.action()) {
            case "generate_template" -> AiPlanConversationRecorder.TEMPLATE;
            case "reject" -> AiPlanConversationRecorder.REJECT;
            default -> throw new ApiException(ErrorCode.PLAN_INFORMATION_INCOMPLETE,
                    "지원하지 않는 템플릿 action입니다: " + response.action());
        };
        conversationRecorder.append(userId, request.conversationId(), request.message(), response, action);
        return response;
    }

    public PlanTurnResponse generate(Long userId, GenerateScheduleRequest request) {
        validateConversation(userId, request.conversationId());
        validateTemplateAnswers(request.templateAnswers());
        PlanTurnResponse response = validatePlanTurn(aiPlanClient.generate(new AiGeneratePayload(
                request.conversationId(), request.scheduleId(), request.goalSummary(), request.category(),
                request.templateAnswers(), emptyIfNull(request.busyDates()), request.longTermContext())));
        conversationRecorder.append(userId, request.conversationId(), request, response,
                AiPlanConversationRecorder.PLAN_TURN);
        return response;
    }

    public PlanTurnResponse revise(Long userId, String scheduleId, ReviseScheduleRequest request) {
        validateConversation(userId, request.conversationId());
        validateTemplateAnswers(request.templateAnswers());
        if (request.currentPlan() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "currentPlan은 필수입니다.");
        }
        PlanTurnResponse response = validatePlanTurn(aiPlanClient.revise(new AiRevisePayload(
                request.conversationId(), scheduleId, request.goalSummary(), request.category(),
                request.templateAnswers(), request.currentPlan(), request.userMessage(),
                request.feedbackHistory() == null ? List.of() : request.feedbackHistory(),
                emptyIfNull(request.busyDates()))));
        if (response.confirmed()) {
            ImageService.ImageResult image = imageService.getRandomByCategoryName(response.category());
            Long savedScheduleId = confirmedPlanPersistenceService.save(
                    userId, request.conversationId(), request.goalSummary(), response.plan());
            response = response.withSavedScheduleId(savedScheduleId);
            response = response.withImage(image.image().getId(), image.url(), image.expiresAt());
        }
        conversationRecorder.append(userId, request.conversationId(), request.userMessage(), response,
                response.confirmed() ? AiPlanConversationRecorder.PLAN_CONFIRMED
                        : AiPlanConversationRecorder.PLAN_TURN);
        return response;
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

    private <T> List<T> emptyIfNull(List<T> value) {
        return value == null ? new ArrayList<>() : value;
    }
}
