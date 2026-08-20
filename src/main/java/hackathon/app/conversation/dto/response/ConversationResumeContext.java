package hackathon.app.conversation.dto.response;

import hackathon.app.ai.plan.dto.SchedulePlan;
import java.util.List;
import java.util.Map;

public record ConversationResumeContext(
        String goalSummary,
        String category,
        Map<String, Object> templateAnswers,
        SchedulePlan currentPlan,
        List<String> feedbackHistory,
        List<String> completedTaskIds
) {}
