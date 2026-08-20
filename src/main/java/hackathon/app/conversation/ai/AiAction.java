package hackathon.app.conversation.ai;

public record AiAction(AiActionType type, String title, Long categoryId) {
    public AiAction {
        if (type == null) throw new IllegalArgumentException("AI action type must not be null");
    }

    public static AiAction continueConversation() {
        return new AiAction(AiActionType.CONTINUE_CONVERSATION, null, null);
    }

    public static AiAction generateSchedule(String title, Long categoryId) {
        return new AiAction(AiActionType.GENERATE_SCHEDULE, title, categoryId);
    }
}
