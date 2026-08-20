package hackathon.app.domain.scheduleitem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** POST /schedules/{scheduleId}/items */
public record ScheduleItemCreateRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
        String title,
        @NotNull(message = "scheduledDate 는 필수입니다.")
        LocalDate scheduledDate,
        @Size(max = 1000, message = "설명은 1000자 이하여야 합니다.")
        String description,
        @NotNull(message = "estimatedMinutes 는 필수입니다.")
        @Min(value = 1, message = "estimatedMinutes 는 1 이상이어야 합니다.")
        @Max(value = 1440, message = "estimatedMinutes 는 1440 이하여야 합니다.")
        Integer estimatedMinutes,
        @NotBlank(message = "itemType 은 필수입니다.")
        String itemType,
        @Min(value = 1, message = "priority 는 1~5 입니다.")
        @Max(value = 5, message = "priority 는 1~5 입니다.")
        Integer priority,
        @Min(value = 0, message = "position 은 0 이상이어야 합니다.")
        Integer position
) {
    /** 2026-08-19 이전 계약을 사용하던 내부 테스트 호환용. */
    @Deprecated
    public ScheduleItemCreateRequest(String title, LocalDate scheduledDate, String description,
                                     Long categoryId, Integer workload, Integer estimatedMinutes,
                                     Integer priority, Integer position) {
        this(title, scheduledDate, description, estimatedMinutes, "ETC", priority, position);
    }
}
