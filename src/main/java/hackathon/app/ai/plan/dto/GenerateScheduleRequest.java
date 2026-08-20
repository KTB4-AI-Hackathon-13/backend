package hackathon.app.ai.plan.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record GenerateScheduleRequest(
        @NotBlank String conversationId,
        @NotBlank @Size(max = 1000) String goalSummary,
        @NotBlank String category,
        Map<String, Object> templateAnswers,
        JsonNode longTermContext
) {}
