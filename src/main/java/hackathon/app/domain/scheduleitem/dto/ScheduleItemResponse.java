package hackathon.app.domain.scheduleitem.dto;

import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.global.common.TimeUtils;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 작업 단건 응답 */
public record ScheduleItemResponse(
        Long id,
        Long scheduleId,
        Long categoryId,
        Long parentItemId,
        String title,
        String description,
        LocalDate scheduledDate,
        int position,
        int workload,
        Integer estimatedMinutes,
        int priority,
        ScheduleItemStatus status,
        OffsetDateTime completedAt
) {
    /** 기존 호출부와의 호환용 생성자. */
    public ScheduleItemResponse(Long id, Long scheduleId, Long categoryId, Long parentItemId,
                                String title, String description, LocalDate scheduledDate,
                                int position, int workload, int priority,
                                ScheduleItemStatus status, OffsetDateTime completedAt) {
        this(id, scheduleId, categoryId, parentItemId, title, description, scheduledDate,
                position, workload, null, priority, status, completedAt);
    }

    public static ScheduleItemResponse from(ScheduleItem item) {
        return new ScheduleItemResponse(
                item.getId(),
                item.getSchedule().getId(),
                item.getCategoryId(),
                item.getParentItemId(),
                item.getTitle(),
                item.getDescription(),
                item.getScheduledDate(),
                item.getPosition(),
                item.getWorkload(),
                item.getEstimatedMinutes(),
                item.getPriority(),
                item.getStatus(),
                TimeUtils.toOffset(item.getCompletedAt())
        );
    }
}
