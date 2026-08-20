package hackathon.app.conversation.dto.response;

public record MessageExchangeResponse(MessageResponse userMessage, MessageResponse assistantMessage,
        PlanReadinessResponse planReadiness) {}
