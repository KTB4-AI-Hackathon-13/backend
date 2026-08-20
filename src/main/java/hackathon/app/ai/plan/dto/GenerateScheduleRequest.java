package hackathon.app.ai.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public record GenerateScheduleRequest(
        @NotBlank String conversationId,
        @NotBlank String scheduleId,
        @NotBlank @Size(max = 1000) String goalSummary,
        @NotBlank String category,
        Map<String, Object> templateAnswers,
        List<JsonNode> busyDates,
        JsonNode longTermContext
) {}
