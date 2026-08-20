package hackathon.app.ai.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FE가 최초 목표를 입력할 때 사용하는 요청. */
public record GenerateTemplateRequest(
        @NotBlank String conversationId,
        @NotBlank @Size(max = 500) String text
) {}
