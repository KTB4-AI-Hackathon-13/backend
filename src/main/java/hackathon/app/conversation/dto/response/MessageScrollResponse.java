package hackathon.app.conversation.dto.response;

import java.util.List;

public record MessageScrollResponse(List<MessageResponse> items, Integer nextCursor, boolean hasNext) {}
