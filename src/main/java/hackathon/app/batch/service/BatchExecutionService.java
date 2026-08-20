package hackathon.app.batch.service;

import hackathon.app.batch.entity.BatchJobType;
import java.time.LocalDate;
import java.util.function.IntSupplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 배치 본문 전·후로 batch_jobs 상태를 독립 트랜잭션에 기록한다. */
@Service
@RequiredArgsConstructor
public class BatchExecutionService {

    private final BatchJobRecorder recorder;

    public void execute(BatchJobType type, LocalDate targetDate, IntSupplier action) {
        Long jobId = recorder.start(type, targetDate);
        try {
            int processedCount = action.getAsInt();
            recorder.succeed(jobId, processedCount);
        } catch (RuntimeException exception) {
            recorder.fail(jobId, exception);
            throw exception;
        }
    }
}
