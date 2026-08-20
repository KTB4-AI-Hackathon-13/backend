package hackathon.app.domain.schedule.dto;

import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.global.common.TimeUtils;
import java.time.OffsetDateTime;

/** 캘린더/오늘 할 일에서 쓰는 작업 요약 (어느 스케줄의 작업인지 포함) */
public record DailyItemResponse(
        Long id,
        Long scheduleId,
        String scheduleTitle,
        Long categoryId,
        String title,
        int position,
        Integer workload,
        Integer estimatedMinutes,
        int priority,
        ScheduleItemStatus status,
        OffsetDateTime completedAt
) {
    public static DailyItemResponse from(ScheduleItem item) {
        return new DailyItemResponse(
                item.getId(),
                item.getSchedule() == null ? null : item.getSchedule().getId(),
                item.getSchedule() == null ? null : item.getSchedule().getTitle(),
                item.getCategoryId(),
                item.getTitle(),
                item.getPosition(),
                item.getWorkload(),
                item.getEstimatedMinutes(),
                item.getPriority(),
                item.getStatus(),
                TimeUtils.toOffset(item.getCompletedAt())
        );
    }
}
