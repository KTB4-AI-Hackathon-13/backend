package hackathon.app.ai.plan.dto;

import java.time.LocalDate;

/** 외부 AI 서버의 snake_case JSON 계약. */
public record AiPlanTask(Long id, LocalDate scheduled_date, String title,
        String description, Integer estimated_min) {}
