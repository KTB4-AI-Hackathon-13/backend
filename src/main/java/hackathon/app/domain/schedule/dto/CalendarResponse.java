package hackathon.app.domain.schedule.dto;

import java.time.LocalDate;
import java.util.List;

/** GET /calendar?year&month — 날짜별 작업 목록 (작업이 있는 날짜만 포함) */
public record CalendarResponse(
        int year,
        int month,
        int totalCount,
        int completedCount,
        List<Day> days
) {
    public record Day(LocalDate date, int totalCount, int completedCount, List<DailyItemResponse> items) {
    }
}
