package hackathon.app.conversation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAlias;

public record MessageRequest(
        @JsonAlias("message")
        @NotBlank @Size(max = 20000)
        String content
) {
    /** 이전 프론트 호출 호환용. */
    public String message() { return content; }
}
