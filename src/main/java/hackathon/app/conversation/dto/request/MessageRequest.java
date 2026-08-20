package hackathon.app.conversation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import hackathon.app.ai.plan.dto.SchedulePlan;
import java.util.List;
import java.util.Map;

public record MessageRequest(
        @NotBlank @Size(max = 20000) String message,
        @NotBlank String goal_summary,
        @NotBlank String category,
        @NotEmpty Map<String, Object> template_answers,
        @NotNull SchedulePlan current_plan,
        List<String> feedback_history,
        Object[] busy_dates
) {}
