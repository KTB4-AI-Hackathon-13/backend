package hackathon.app.domain.scheduleitem.dto;

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
        Integer estimatedMinutes,
        @NotBlank(message = "itemType 은 필수입니다.")
        String itemType,
        Integer position
) {
    /** 2026-08-19 이전 계약을 사용하던 내부 테스트 호환용. */
    @Deprecated
    public ScheduleItemCreateRequest(String title, LocalDate scheduledDate, String description,
                                     Long categoryId, Integer workload, Integer estimatedMinutes,
                                     Integer priority, Integer position) {
        this(title, scheduledDate, description, estimatedMinutes, "ETC", position);
    }
}
