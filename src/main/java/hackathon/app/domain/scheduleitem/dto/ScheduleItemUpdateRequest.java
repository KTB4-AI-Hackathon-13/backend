package hackathon.app.domain.scheduleitem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** PATCH /schedule-items/{itemId} — null 인 필드는 변경하지 않는다. 상태는 /status 로만 변경. */
public record ScheduleItemUpdateRequest(
        @Size(min = 1, max = 100, message = "제목은 1~100자여야 합니다.")
        String title,
        @Size(max = 1000, message = "설명은 1000자 이하여야 합니다.")
        String description,
        LocalDate scheduledDate,
        @Min(value = 1, message = "estimatedMinutes 는 1 이상이어야 합니다.")
        @Max(value = 1440, message = "estimatedMinutes 는 1440 이하여야 합니다.")
        Integer estimatedMinutes,
        String itemType,
        @Min(value = 1, message = "priority 는 1~5 입니다.")
        @Max(value = 5, message = "priority 는 1~5 입니다.")
        Integer priority,
        @Min(value = 0, message = "position 은 0 이상이어야 합니다.")
        Integer position
) {
    /** 2026-08-19 이전 계약을 사용하던 내부 테스트 호환용. */
    @Deprecated
    public ScheduleItemUpdateRequest(String title, String description, LocalDate scheduledDate,
                                     Long categoryId, Integer workload, Integer estimatedMinutes,
                                     Integer priority, Integer position) {
        this(title, description, scheduledDate, estimatedMinutes, null, priority, position);
    }

    public boolean isEmpty() {
        return title == null && description == null && scheduledDate == null
                && estimatedMinutes == null && itemType == null && priority == null && position == null;
    }
}
