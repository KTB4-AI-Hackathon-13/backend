package hackathon.app.domain.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 사용자가 직접 스케줄을 생성할 때 사용하는 요청. */
public record ScheduleCreateRequest(
        @NotBlank
        @Size(max = 200, message = "제목은 1~200자여야 합니다.")
        String title,

        @Size(max = 5000, message = "설명은 5000자 이하여야 합니다.")
        String description,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate,

        Long categoryId
) {
    public ScheduleCreateRequest(String title, String description, LocalDate startDate, LocalDate endDate) {
        this(title, description, startDate, endDate, null);
    }
}
