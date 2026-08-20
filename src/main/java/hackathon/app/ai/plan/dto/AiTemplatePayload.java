package hackathon.app.ai.plan.dto;

/** 외부 AI 서버 POST /templates 요청 계약. */
public record AiTemplatePayload(String conversation_id, String text) {}
