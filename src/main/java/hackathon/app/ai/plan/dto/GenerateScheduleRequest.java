package hackathon.app.ai.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

public record GenerateScheduleRequest(
        @NotBlank String conversation_id,
        @NotBlank String goal_summary,
        @NotBlank String category,
        @NotEmpty Map<String, Object> template_answers,
        Object[] busy_dates,
        Object long_term_context
) {}
