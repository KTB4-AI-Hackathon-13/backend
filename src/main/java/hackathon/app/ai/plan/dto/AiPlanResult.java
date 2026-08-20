package hackathon.app.ai.plan.dto;

import java.util.List;

public record AiPlanResult(String summary, List<AiPlanTask> tasks) {}
