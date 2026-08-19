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
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title,
        @NotNull(message = "scheduledDate 는 필수입니다.")
        LocalDate scheduledDate,
        @Size(max = 5000, message = "설명은 5000자 이하여야 합니다.")
        String description,
        Long categoryId,
        @Min(value = 1, message = "workload 는 1 이상이어야 합니다.")
        Integer workload,
        @Min(value = 1, message = "priority 는 1~5 입니다.") @Max(value = 5, message = "priority 는 1~5 입니다.")
        Integer priority,
        @Min(value = 0, message = "position 은 0 이상이어야 합니다.")
        Integer position
) {
}
