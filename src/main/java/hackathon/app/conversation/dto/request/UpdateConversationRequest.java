package hackathon.app.conversation.dto.request;

import hackathon.app.conversation.domain.ConversationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateConversationRequest(@NotNull ConversationStatus status) {}
