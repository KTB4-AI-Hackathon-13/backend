package hackathon.app.ai.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public record ReviseScheduleRequest(
        @NotBlank String conversationId,
        @NotBlank @Size(max = 1000) String goalSummary,
        @NotBlank String category,
        Map<String, Object> templateAnswers,
        JsonNode currentPlan,
        @NotBlank @Size(max = 5000) String userMessage,
        List<String> feedbackHistory,
        List<JsonNode> busyDates
) {}
