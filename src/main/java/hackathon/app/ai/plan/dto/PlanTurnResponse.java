package hackathon.app.ai.plan.dto;

import java.util.List;
import tools.jackson.databind.JsonNode;

/** /plan/generate와 /plan/revise의 공통 응답 계약. */
public record PlanTurnResponse(
        String assistant_message,
        JsonNode plan,
        boolean ready_to_confirm,
        boolean confirmed,
        Boolean submitted,
        List<String> feedback_history
) {}
