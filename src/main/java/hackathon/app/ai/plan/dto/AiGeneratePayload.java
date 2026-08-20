package hackathon.app.ai.plan.dto;

import java.util.Map;

public record AiGeneratePayload(
        String goal_summary,
        String category,
        Map<String, Object> template_answers,
        Object[] busy_dates,
        Object long_term_context
) {}
