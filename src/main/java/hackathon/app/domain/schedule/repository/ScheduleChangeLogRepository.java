package hackathon.app.domain.schedule.repository;

import hackathon.app.domain.schedule.entity.ScheduleChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleChangeLogRepository extends JpaRepository<ScheduleChangeLog, Long> {
}
