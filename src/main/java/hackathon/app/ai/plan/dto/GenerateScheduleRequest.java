package hackathon.app.ai.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GenerateScheduleRequest(@NotBlank String conversationId,
        @NotBlank @Size(max = 200) String title, @NotNull Long categoryId) {}
