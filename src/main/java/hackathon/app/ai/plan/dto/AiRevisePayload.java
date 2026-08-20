package hackathon.app.ai.plan.dto;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public record AiRevisePayload(
        String conversation_id,
        String schedule_id,
        String goal_summary,
        String category,
        Map<String, Object> template_answers,
        SchedulePlan current_plan,
        String user_message,
        List<String> feedback_history,
        List<JsonNode> busy_dates
) {}
