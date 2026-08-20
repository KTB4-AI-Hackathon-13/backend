package hackathon.app.batch.service;

import hackathon.app.batch.entity.BatchJob;
import hackathon.app.batch.entity.BatchJobType;
import hackathon.app.batch.repository.BatchJobRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 본 배치 트랜잭션이 롤백되어도 실패 기록을 남기기 위한 별도 트랜잭션 기록기. */
@Service
@RequiredArgsConstructor
public class BatchJobRecorder {

    private static final int MAX_ERROR_LENGTH = 4000;

    private final BatchJobRepository repository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(BatchJobType type, LocalDate targetDate) {
        LocalDateTime now = LocalDateTime.now(clock);
        BatchJob job = repository.findByJobTypeAndTargetDate(type, targetDate)
                .orElseGet(() -> BatchJob.create(type, targetDate, now));
        if (job.getId() != null) {
            job.restart(now);
        }
        return repository.save(job).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(Long jobId, int processedCount) {
        repository.findById(jobId).ifPresent(job ->
                job.succeed(processedCount, LocalDateTime.now(clock)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long jobId, RuntimeException exception) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        if (message.length() > MAX_ERROR_LENGTH) {
            message = message.substring(0, MAX_ERROR_LENGTH);
        }
        String finalMessage = message;
        repository.findById(jobId).ifPresent(job ->
                job.fail(finalMessage, LocalDateTime.now(clock)));
    }
}
