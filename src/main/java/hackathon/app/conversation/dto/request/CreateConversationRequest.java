package hackathon.app.conversation.dto.request;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(@Size(max = 200) String title) {}
