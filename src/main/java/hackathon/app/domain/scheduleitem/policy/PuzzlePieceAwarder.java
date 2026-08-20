package hackathon.app.domain.scheduleitem.policy;

import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;

/**
 * 작업이 COMPLETED 가 될 때 퍼즐 조각을 지급하는 포트.
 *
 * 계약:
 * - 같은 작업에 대해 여러 번 호출되어도 조각은 최대 1개만 만들어져야 한다 (핵심 정책 4).
 *   → puzzle_pieces.schedule_item_id UNIQUE 로 보장하고, 이미 있으면 awarded=false 로 반환.
 * - 스케줄에 퍼즐이 없으면 구현체가 만든다 (스케줄 1개 = 퍼즐 1개).
 *
 * 구현: hackathon.app.domain.puzzle.service.PuzzlePieceAwardService (7번 퍼즐 도메인).
 */
public interface PuzzlePieceAwarder {

    /** @param awarded 이번 호출에서 새 조각을 만들었는지, @param puzzlePieceId 만들었다면 그 id (아니면 null) */
    record AwardResult(boolean awarded, Long puzzlePieceId) {
        public static AwardResult none() {
            return new AwardResult(false, null);
        }
    }

    AwardResult awardOnComplete(ScheduleItem item);

    /**
     * 스케줄의 유효한 작업 구성이 바뀌었을 때(작업 추가·삭제·취소 등) 퍼즐 완성 여부를 다시 계산한다.
     * 퍼즐이 아직 없으면 아무것도 하지 않는다.
     */
    void refreshOnItemsChanged(Schedule schedule);
}
