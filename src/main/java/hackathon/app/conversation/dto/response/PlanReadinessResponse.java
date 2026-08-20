package hackathon.app.conversation.dto.response;

import java.util.List;

public record PlanReadinessResponse(boolean ready, List<String> missingFields) {}
