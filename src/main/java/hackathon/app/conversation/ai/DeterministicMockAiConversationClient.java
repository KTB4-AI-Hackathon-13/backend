package hackathon.app.conversation.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class DeterministicMockAiConversationClient implements AiConversationClient {
    @Override
    public AiConversationResult reply(String userContent) {
        return AiConversationResult.continueConversation(
                // 수정 필요
                "계획을 구체화하고 있어요. 목표 종료일과 가능한 요일을 알려주세요.",
                "deterministic-mock-v1");
    }
}
