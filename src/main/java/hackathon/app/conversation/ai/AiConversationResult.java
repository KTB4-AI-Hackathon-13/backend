package hackathon.app.conversation.ai;

public record AiConversationResult(String content, String modelName, AiAction action, AiTokenUsage tokenUsage) {
    public AiConversationResult {
        action = action == null ? AiAction.continueConversation() : action;
        tokenUsage = tokenUsage == null ? AiTokenUsage.unknown() : tokenUsage;
    }

    public static AiConversationResult continueConversation(String content, String modelName) {
        return new AiConversationResult(
                content, modelName, AiAction.continueConversation(), AiTokenUsage.unknown());
    }
}
