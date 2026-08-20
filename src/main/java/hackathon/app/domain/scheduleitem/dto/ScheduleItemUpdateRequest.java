package hackathon.app.domain.scheduleitem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** PATCH /schedule-items/{itemId} — null 인 필드는 변경하지 않는다. 상태는 /status 로만 변경. */
public record ScheduleItemUpdateRequest(
        @Size(min = 1, max = 200, message = "제목은 1~200자여야 합니다.")
        String title,
        @Size(max = 5000, message = "설명은 5000자 이하여야 합니다.")
        String description,
        LocalDate scheduledDate,
        Long categoryId,
        @Min(value = 1, message = "workload 는 1 이상이어야 합니다.")
        Integer workload,
        @Min(value = 1, message = "estimatedMinutes 는 1 이상이어야 합니다.")
        Integer estimatedMinutes,
        @Min(value = 1, message = "priority 는 1~5 입니다.") @Max(value = 5, message = "priority 는 1~5 입니다.")
        Integer priority,
        @Min(value = 0, message = "position 은 0 이상이어야 합니다.")
        Integer position
) {
    public boolean isEmpty() {
        return title == null && description == null && scheduledDate == null && categoryId == null
                && workload == null && estimatedMinutes == null && priority == null && position == null;
    }
}
