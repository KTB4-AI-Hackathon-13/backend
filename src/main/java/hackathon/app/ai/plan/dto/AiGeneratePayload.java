package hackathon.app.ai.plan.dto;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public record AiGeneratePayload(
        String conversation_id,
        String schedule_id,
        String goal_summary,
        String category,
        Map<String, Object> template_answers,
        List<JsonNode> busy_dates,
        JsonNode long_term_context
) {}
