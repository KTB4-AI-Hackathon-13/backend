package hackathon.app.ai.plan.dto;

import hackathon.app.ai.plan.entity.AiGenerationStatus;

public record AiGenerationAcceptedResponse(String generationId, AiGenerationStatus status) {}
