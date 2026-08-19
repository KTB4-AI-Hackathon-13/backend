package hackathon.app.domain.schedule.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** PATCH /schedules/{scheduleId} — null 인 필드는 변경하지 않는다. */
public record ScheduleUpdateRequest(
        @Size(min = 1, max = 200, message = "제목은 1~200자여야 합니다.")
        String title,
        @Size(max = 5000, message = "설명은 5000자 이하여야 합니다.")
        String description,
        LocalDate startDate,
        LocalDate endDate
) {
    public boolean isEmpty() {
        return title == null && description == null && startDate == null && endDate == null;
    }
}
