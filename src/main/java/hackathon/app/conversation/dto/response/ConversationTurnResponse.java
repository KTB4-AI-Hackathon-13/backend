package hackathon.app.conversation.dto.response;

import hackathon.app.ai.plan.dto.SchedulePlan;
import java.util.List;

public record ConversationTurnResponse(
        String assistant_message,
        SchedulePlan plan,
        boolean ready_to_confirm,
        boolean confirmed,
        Boolean submitted,
        List<String> feedback_history,
        Long schedule_id
) {}
