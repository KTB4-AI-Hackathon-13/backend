package hackathon.app.domain.schedule.dto;

import java.time.LocalDate;
import java.util.List;

/** GET /schedule-items/today — 오늘 작업, 완료 수, 전체 수 */
public record TodayItemsResponse(
        LocalDate date,
        int totalCount,
        int completedCount,
        List<DailyItemResponse> items
) {
}
