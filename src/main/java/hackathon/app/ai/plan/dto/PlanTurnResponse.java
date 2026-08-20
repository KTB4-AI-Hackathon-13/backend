package hackathon.app.ai.plan.dto;

import java.util.List;

/** /plan/generate와 /plan/revise의 공통 응답 계약. */
public record PlanTurnResponse(
        String assistant_message,
        SchedulePlan plan,
        boolean ready_to_confirm,
        boolean confirmed,
        Boolean submitted,
        List<String> feedback_history,
        Long saved_schedule_id
) {
    public PlanTurnResponse withSavedScheduleId(Long scheduleId) {
        return new PlanTurnResponse(assistant_message, plan, ready_to_confirm, confirmed,
                true, feedback_history, scheduleId);
    }
}
