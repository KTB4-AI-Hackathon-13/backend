package hackathon.app.ai.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public record GenerateScheduleRequest(
        @NotBlank String conversationId,
        @NotBlank String goalSummary,
        @NotBlank String category,
        @NotEmpty Map<String, Object> templateAnswers,
        List<BusyDatePayload> busyDates,
        JsonNode longTermContext
) {}
