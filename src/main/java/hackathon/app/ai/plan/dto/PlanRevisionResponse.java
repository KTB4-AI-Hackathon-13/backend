package hackathon.app.ai.plan.dto;

import java.time.Instant;
import java.util.List;

/** ai-revisions가 FE에 반환하는 응답. 이미지 값은 이 응답에서만 노출한다. */
public record PlanRevisionResponse(
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
    public static PlanRevisionResponse from(PlanTurnResponse planTurn, Long imageId,
                                            String imageUrl, Instant imageUrlExpiresAt) {
        return new PlanRevisionResponse(
                planTurn.assistant_message(),
                planTurn.plan(),
                planTurn.ready_to_confirm(),
                planTurn.confirmed(),
                planTurn.submitted(),
                planTurn.feedback_history(),
                planTurn.saved_schedule_id(),
                planTurn.category(),
                imageId,
                imageUrl,
                imageUrlExpiresAt);
    }
}
