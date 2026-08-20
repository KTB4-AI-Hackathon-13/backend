package hackathon.app.domain.scheduleitem.dto;

import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemType;
import hackathon.app.global.common.TimeUtils;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 작업 단건 응답 */
public record ScheduleItemResponse(
        Long id,
        Long scheduleId,
        Long parentItemId,
        String title,
        String description,
        LocalDate scheduledDate,
        int position,
        Integer estimatedMinutes,
        ScheduleItemType itemType,
        int priority,
        ScheduleItemStatus status,
        OffsetDateTime completedAt
) {
    /** 기존 호출부와의 호환용 생성자. */
    public ScheduleItemResponse(Long id, Long scheduleId, Long categoryId, Long parentItemId,
                                String title, String description, LocalDate scheduledDate,
                                int position, int workload, int priority,
                                ScheduleItemStatus status, OffsetDateTime completedAt) {
        this(id, scheduleId, parentItemId, title, description, scheduledDate,
                position, workload, ScheduleItemType.ETC, priority, status, completedAt);
    }

    /** 이전 테스트가 조회하던 값. API JSON에는 포함되지 않는다. */
    @Deprecated public Long categoryId() { return null; }
    @Deprecated public Integer workload() { return null; }

    public static ScheduleItemResponse from(ScheduleItem item) {
        return new ScheduleItemResponse(
                item.getId(),
                item.getSchedule() == null ? null : item.getSchedule().getId(),
                item.getParentItemId(),
                item.getTitle(),
                item.getDescription(),
                item.getScheduledDate(),
                item.getPosition(),
                item.getEstimatedMinutes(),
                item.getItemType(),
                item.getPriority(),
                item.getStatus(),
                TimeUtils.toOffset(item.getCompletedAt())
        );
    }
}
