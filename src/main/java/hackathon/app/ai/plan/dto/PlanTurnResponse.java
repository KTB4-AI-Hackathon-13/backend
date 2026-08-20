package hackathon.app.ai.plan.dto;

import java.time.Instant;
import java.util.List;

/** /plan/generate와 /plan/revise의 공통 응답 계약. */
public record PlanTurnResponse(
        String assistant_message,
        SchedulePlan plan,
        boolean ready_to_confirm,
        boolean confirmed,
        Boolean submitted,
        List<String> feedback_history,
        Long saved_schedule_id,
        String category,
        Long image_id,
        String image_url,
        Instant image_url_expires_at
) {
    public PlanTurnResponse withSavedScheduleId(Long scheduleId) {
        return new PlanTurnResponse(assistant_message, plan, ready_to_confirm, confirmed,
                true, feedback_history, scheduleId, category, image_id, image_url, image_url_expires_at);
    }

    public PlanTurnResponse withImage(Long imageId, String imageUrl, Instant expiresAt) {
        return new PlanTurnResponse(assistant_message, plan, ready_to_confirm, confirmed,
                submitted, feedback_history, saved_schedule_id, category, imageId, imageUrl, expiresAt);
    }
}
