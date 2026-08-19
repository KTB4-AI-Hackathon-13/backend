package hackathon.app.domain.schedule.repository;

import hackathon.app.domain.schedule.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** findById 는 @SQLRestriction 덕분에 미삭제 스케줄만 반환한다. 소유자 검사는 서비스에서 수행 (403). */
public interface ScheduleRepository extends JpaRepository<Schedule, Long>, JpaSpecificationExecutor<Schedule> {
}
