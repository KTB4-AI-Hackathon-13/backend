package hackathon.app.ai.plan.dto;

import java.util.List;

public record AiRevisePayload(Long schedule_id, String conversation_id, String instruction,
        String summary, List<AiPlanTask> tasks) {}
