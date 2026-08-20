package hackathon.app.conversation;

import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;

public class ConversationException extends ApiException {
    private ConversationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public static ConversationException notFound() {
        return new ConversationException(ErrorCode.CONVERSATION_NOT_FOUND);
    }

    public static ConversationException messageNotFound() {
        return new ConversationException(ErrorCode.MESSAGE_NOT_FOUND);
    }

    public static ConversationException archived() {
        return new ConversationException(ErrorCode.CONVERSATION_ARCHIVED);
    }

    public static ConversationException invalidStatus() {
        return new ConversationException(ErrorCode.INVALID_CONVERSATION_STATUS);
    }
}
