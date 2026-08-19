package hackathon.app.domain.scheduleitem.policy;

import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import org.springframework.stereotype.Component;

/** [임시] 퍼즐 도메인(7번) 구현 전까지 조각을 지급하지 않는다. */
@Component
public class NoOpPuzzlePieceAwarder implements PuzzlePieceAwarder {

    @Override
    public AwardResult awardOnComplete(ScheduleItem item) {
        return AwardResult.none();
    }
}
