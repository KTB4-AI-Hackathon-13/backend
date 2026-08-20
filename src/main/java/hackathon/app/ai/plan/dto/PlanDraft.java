package hackathon.app.ai.plan.dto;

import java.time.LocalDate;
import java.util.List;

/** 대화 메시지 payload_json과 AI 생성 작업 사이의 공통 초안 계약. */
public record PlanDraft(String title, LocalDate startDate, LocalDate endDate, List<Item> items) {
    public record Item(LocalDate scheduledDate, String title, Integer estimatedMinutes,
                       String itemType, String description) {}
}
