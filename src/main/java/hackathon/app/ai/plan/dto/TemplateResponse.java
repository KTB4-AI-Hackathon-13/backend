package hackathon.app.ai.plan.dto;

import java.util.List;

/** 외부 AI의 템플릿 응답을 FE에 그대로 전달하기 위한 DTO. */
public record TemplateResponse(String action, Payload payload) {

    public record Payload(
            String category,
            String goal_summary,
            List<Question> questions
    ) {}

    public record Question(
            String id,
            String label,
            String type,
            List<String> options
    ) {}
}
