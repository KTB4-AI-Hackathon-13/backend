package hackathon.app.ai.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiRevisionCreateRequest(
        @NotBlank @Size(max = 5000) String instruction,
        @NotBlank String conversationId
) {}
