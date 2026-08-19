package hackathon.app.domain.scheduleitem.dto;

import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.policy.PuzzlePieceAwarder;
import hackathon.app.global.common.TimeUtils;
import java.time.OffsetDateTime;

/** 작업 상태 변경 응답 — 설계서 "작업 완료 응답" 필드 */
public record ScheduleItemStatusResponse(
        Long itemId,
        ScheduleItemStatus status,
        OffsetDateTime completedAt,
        boolean puzzlePieceAwarded,
        Long puzzlePieceId
) {
    public static ScheduleItemStatusResponse of(ScheduleItem item, PuzzlePieceAwarder.AwardResult award) {
        return new ScheduleItemStatusResponse(
                item.getId(),
                item.getStatus(),
                TimeUtils.toOffset(item.getCompletedAt()),
                award.awarded(),
                award.puzzlePieceId()
        );
    }
}
