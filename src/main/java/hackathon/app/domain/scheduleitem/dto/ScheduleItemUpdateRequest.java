package hackathon.app.domain.scheduleitem.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** PATCH /schedule-items/{itemId} — null 인 필드는 변경하지 않는다. 상태는 /status 로만 변경. */
public record ScheduleItemUpdateRequest(
        @Size(min = 1, max = 100, message = "제목은 1~100자여야 합니다.")
        String title,
        @Size(max = 1000, message = "설명은 1000자 이하여야 합니다.")
        String description,
        LocalDate scheduledDate,
        Integer estimatedMinutes,
        String itemType,
        Integer position
) {
    /** 2026-08-19 이전 계약을 사용하던 내부 테스트 호환용. */
    @Deprecated
    public ScheduleItemUpdateRequest(String title, String description, LocalDate scheduledDate,
                                     Long categoryId, Integer workload, Integer estimatedMinutes,
                                     Integer priority, Integer position) {
        this(title, description, scheduledDate, estimatedMinutes, null, position);
    }

    public boolean isEmpty() {
        return title == null && description == null && scheduledDate == null
                && estimatedMinutes == null && itemType == null && position == null;
    }
}
