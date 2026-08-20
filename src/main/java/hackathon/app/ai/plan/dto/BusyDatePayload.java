package hackathon.app.ai.plan.dto;

import java.time.LocalDate;
/** AI /plan/generate에 전달하는 날짜별 기존 일정 힌트. */
public record BusyDatePayload(LocalDate date, int event_count) {}
