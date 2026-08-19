package hackathon.app.domain.scheduleitem.policy;

import hackathon.app.domain.scheduleitem.entity.ScheduleItem;

/**
 * 작업이 COMPLETED 가 될 때 퍼즐 조각을 지급하는 포트.
 *
 * 계약:
 * - 같은 작업에 대해 여러 번 호출되어도 조각은 최대 1개만 만들어져야 한다 (핵심 정책 4).
 *   → puzzle_pieces.schedule_item_id UNIQUE 로 보장하고, 이미 있으면 awarded=false 로 반환.
 * - 스케줄에 퍼즐이 아직 없으면 만들지 말지는 구현체(7번 퍼즐 도메인)가 결정한다.
 *
 * 현재는 {@link NoOpPuzzlePieceAwarder} (항상 미지급). 7번 담당자가 구현체를 만들면 그 빈에 @Primary 를 붙이거나
 * NoOp 구현을 제거한다.
 */
public interface PuzzlePieceAwarder {

    /** @param awarded 이번 호출에서 새 조각을 만들었는지, @param puzzlePieceId 만들었다면 그 id (아니면 null) */
    record AwardResult(boolean awarded, Long puzzlePieceId) {
        public static AwardResult none() {
            return new AwardResult(false, null);
        }
    }

    AwardResult awardOnComplete(ScheduleItem item);
}
