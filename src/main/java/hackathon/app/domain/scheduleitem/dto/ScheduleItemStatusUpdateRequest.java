package hackathon.app.domain.scheduleitem.dto;

import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import jakarta.validation.constraints.NotNull;

/** PATCH /schedule-items/{itemId}/status */
public record ScheduleItemStatusUpdateRequest(
        @NotNull(message = "status 는 필수입니다.")
        ScheduleItemStatus status
) {
}
