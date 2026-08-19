package hackathon.app.domain.schedule.repository;

import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.schedule.entity.ScheduleStatus;
import org.springframework.data.jpa.domain.Specification;

/** 스케줄 목록 조회용 동적 조건 (status?, cursor?) */
public final class ScheduleSpecs {

    private ScheduleSpecs() {
    }

    public static Specification<Schedule> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<Schedule> hasStatus(ScheduleStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    /** id 내림차순 페이징에서 커서(마지막 id)보다 작은 행만 */
    public static Specification<Schedule> idLessThan(Long cursorId) {
        return (root, query, cb) -> cursorId == null ? null : cb.lessThan(root.get("id"), cursorId);
    }
}
