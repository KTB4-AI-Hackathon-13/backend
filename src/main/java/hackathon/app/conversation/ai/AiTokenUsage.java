package hackathon.app.conversation.ai;

public record AiTokenUsage(Integer promptTokens, Integer completionTokens) {
    public static AiTokenUsage unknown() {
        return new AiTokenUsage(null, null);
    }
}
