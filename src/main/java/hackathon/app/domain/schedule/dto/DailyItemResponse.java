package hackathon.app.domain.schedule.dto;

import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemType;
import hackathon.app.global.common.TimeUtils;
import java.time.OffsetDateTime;

/** 캘린더/오늘 할 일에서 쓰는 작업 요약 (어느 스케줄의 작업인지 포함) */
public record DailyItemResponse(
        Long id,
        Long scheduleId,
        String scheduleTitle,
        String title,
        String description,
        java.time.LocalDate scheduledDate,
        int position,
        Integer estimatedMinutes,
        ScheduleItemType itemType,
        int priority,
        ScheduleItemStatus status,
        OffsetDateTime completedAt
) {
    /** 2026-08-19 이전 내부 테스트 호환용. */
    @Deprecated
    public DailyItemResponse(Long id, Long scheduleId, String scheduleTitle, Long categoryId,
                             String title, int position, Integer workload, Integer estimatedMinutes,
                             int priority, ScheduleItemStatus status, OffsetDateTime completedAt) {
        this(id, scheduleId, scheduleTitle, title, null, null, position, estimatedMinutes,
                ScheduleItemType.ETC, priority, status, completedAt);
    }

    public static DailyItemResponse from(ScheduleItem item) {
        return new DailyItemResponse(
                item.getId(),
                item.getSchedule() == null ? null : item.getSchedule().getId(),
                item.getSchedule() == null ? null : item.getSchedule().getTitle(),
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
