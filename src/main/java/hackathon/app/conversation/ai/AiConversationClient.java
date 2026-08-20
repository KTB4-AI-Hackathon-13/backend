package hackathon.app.conversation.ai;

public interface AiConversationClient {
    AiConversationResult reply(String userContent);
}
