package hackathon.app.batch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 날짜·종류별 배치 실행 기록. 동일 재실행은 같은 행을 갱신한다. */
@Entity
@Table(name = "batch_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false)
    private BatchJobType jobType;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BatchJobStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static BatchJob create(BatchJobType type, LocalDate targetDate, LocalDateTime now) {
        BatchJob job = new BatchJob();
        job.jobType = type;
        job.targetDate = targetDate;
        job.createdAt = now;
        job.restart(now);
        return job;
    }

    public void restart(LocalDateTime now) {
        status = BatchJobStatus.RUNNING;
        startedAt = now;
        finishedAt = null;
        totalCount = 0;
        successCount = 0;
        failureCount = 0;
        errorMessage = null;
    }

    public void succeed(int processedCount, LocalDateTime now) {
        status = BatchJobStatus.SUCCEEDED;
        totalCount = processedCount;
        successCount = processedCount;
        failureCount = 0;
        errorMessage = null;
        finishedAt = now;
    }

    public void fail(String message, LocalDateTime now) {
        status = BatchJobStatus.FAILED;
        totalCount = Math.max(totalCount, 1);
        successCount = 0;
        failureCount = Math.max(failureCount, 1);
        errorMessage = message;
        finishedAt = now;
    }
}
