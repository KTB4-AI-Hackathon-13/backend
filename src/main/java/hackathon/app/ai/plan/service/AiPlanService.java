package hackathon.app.ai.plan.service;

import hackathon.app.ai.plan.client.AiPlanClient;
import hackathon.app.ai.plan.dto.*;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.conversation.ConversationRepository;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final ScheduleItemRepository scheduleItemRepository;

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
        DateRange range = validateTemplateAnswers(request.templateAnswers());
        List<BusyDatePayload> busyDates = buildBusyDates(userId, range);
        PlanTurnResponse response = validatePlanTurn(aiPlanClient.generate(new AiGeneratePayload(
                request.conversationId(), request.goalSummary(), request.category(),
                request.templateAnswers(), busyDates, request.longTermContext())));
        conversationRecorder.append(userId, request.conversationId(), request, response,
                AiPlanConversationRecorder.PLAN_TURN);
        return response;
    }

    public PlanRevisionResponse revise(Long userId, String scheduleId, ReviseScheduleRequest request) {
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
        Long imageId = null;
        String imageUrl = null;
        java.time.Instant imageUrlExpiresAt = null;
        if (response.confirmed()) {
            ImageService.ImageResult image = imageService.getRandomByCategoryName(response.category());
            Long savedScheduleId = confirmedPlanPersistenceService.save(
                    userId, request.conversationId(), request.goalSummary(), response.category(), response.plan(),
                    image.image().getId());
            response = response.withSavedScheduleId(savedScheduleId);
            imageId = image.image().getId();
            imageUrl = image.url();
            imageUrlExpiresAt = image.expiresAt();
        }
        conversationRecorder.append(userId, request.conversationId(), request.userMessage(), response,
                response.confirmed() ? AiPlanConversationRecorder.PLAN_CONFIRMED
                        : AiPlanConversationRecorder.PLAN_TURN);
        return PlanRevisionResponse.from(response, imageId, imageUrl, imageUrlExpiresAt);
    }

    private void validateConversation(Long userId, String conversationId) {
        if (conversationRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(conversationId, userId).isEmpty()) {
            throw new ApiException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
    }

    private DateRange validateTemplateAnswers(Map<String, Object> answers) {
        if (answers == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "templateAnswers는 필수입니다.");
        }
        LocalDate start = dateAnswer(answers, "start_date");
        LocalDate end = dateAnswer(answers, "end_date");
        if (start.isAfter(end)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "start_date는 end_date보다 늦을 수 없습니다.");
        }
        return new DateRange(start, end);
    }

    private LocalDate dateAnswer(Map<String, Object> answers, String key) {
        Object value = answers.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "templateAnswers." + key + "는 필수입니다.");
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "templateAnswers." + key + "는 YYYY-MM-DD 형식이어야 합니다.");
        }
    }

    private List<BusyDatePayload> buildBusyDates(Long userId, DateRange range) {
        List<ScheduleItem> items = scheduleItemRepository.findByUserIdAndDateBetween(
                userId, range.start(), range.end());
        Map<LocalDate, Integer> countsByDate = new LinkedHashMap<>();
        for (ScheduleItem item : items) {
            if (item.getStatus() != ScheduleItemStatus.TODO
                    && item.getStatus() != ScheduleItemStatus.IN_PROGRESS
                    && item.getStatus() != ScheduleItemStatus.SKIPPED) {
                continue;
            }
            countsByDate.merge(item.getScheduledDate(), 1, Integer::sum);
        }
        return countsByDate.entrySet().stream()
                .map(entry -> new BusyDatePayload(entry.getKey(), entry.getValue()))
                .toList();
    }

    private record DateRange(LocalDate start, LocalDate end) {}

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
