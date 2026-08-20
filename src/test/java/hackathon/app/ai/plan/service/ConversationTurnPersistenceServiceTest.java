package hackathon.app.ai.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import hackathon.app.ai.plan.dto.DailyTask;
import hackathon.app.ai.plan.dto.PlanTurnResponse;
import hackathon.app.ai.plan.dto.SchedulePlan;
import hackathon.app.conversation.ConversationService;
import hackathon.app.conversation.dto.request.MessageRequest;
import hackathon.app.image.service.ImageService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConversationTurnPersistenceServiceTest {

    private final ConversationService conversationService = mock(ConversationService.class);
    private final ConfirmedPlanPersistenceService planPersistenceService =
            mock(ConfirmedPlanPersistenceService.class);
    private final ImageService imageService = mock(ImageService.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final ConversationTurnPersistenceService service = new ConversationTurnPersistenceService(
            conversationService, planPersistenceService, imageService, objectMapper);

    @Test
    void selectsCategoryImageAndPassesItToConfirmedPlanPersistence() {
        SchedulePlan plan = plan();
        MessageRequest request = request(plan);
        PlanTurnResponse result = new PlanTurnResponse(
                "계획을 확정했어요.", plan, true, true, true, List.of(), null, "음악");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(imageService.getRandomImageIdByCategoryName("음악")).thenReturn(77L);
        when(planPersistenceService.save(5L, "conversation", "노래 실력 향상", "음악", plan, 77L))
                .thenReturn(12L);

        Long scheduleId = service.persist(5L, "conversation", request, result);

        assertThat(scheduleId).isEqualTo(12L);
        verify(imageService).getRandomImageIdByCategoryName("음악");
        verify(planPersistenceService)
                .save(5L, "conversation", "노래 실력 향상", "음악", plan, 77L);
    }

    @Test
    void doesNotSelectImageBeforePlanIsConfirmed() {
        SchedulePlan plan = plan();
        MessageRequest request = request(plan);
        PlanTurnResponse result = new PlanTurnResponse(
                "계획을 더 다듬어 볼게요.", plan, false, false, null, List.of(), null, "음악");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Long scheduleId = service.persist(5L, "conversation", request, result);

        assertThat(scheduleId).isNull();
        verifyNoInteractions(imageService, planPersistenceService);
    }

    private SchedulePlan plan() {
        return new SchedulePlan("매일 발성 연습", List.of(
                new DailyTask(null, LocalDate.of(2026, 8, 21), "복식 호흡 익히기", null, 30)));
    }

    private MessageRequest request(SchedulePlan plan) {
        return new MessageRequest(
                "이대로 확정해줘", "노래 실력 향상", "음악", Map.of("days", 15),
                plan, List.of(), List.of());
    }
}
