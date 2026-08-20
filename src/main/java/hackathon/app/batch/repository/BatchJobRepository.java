package hackathon.app.batch.repository;

import hackathon.app.batch.entity.BatchJob;
import hackathon.app.batch.entity.BatchJobType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchJobRepository extends JpaRepository<BatchJob, Long> {
    Optional<BatchJob> findByJobTypeAndTargetDate(BatchJobType jobType, LocalDate targetDate);
}
