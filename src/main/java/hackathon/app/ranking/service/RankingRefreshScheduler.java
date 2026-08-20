package hackathon.app.ranking.service;

import hackathon.app.batch.entity.BatchJobType;
import hackathon.app.batch.service.BatchExecutionService;
import hackathon.app.metrics.service.UserDailyMetricService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 전체·카테고리, 기간별 랭킹을 주기적으로 다시 계산한다. */
@Component
@RequiredArgsConstructor
public class RankingRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(RankingRefreshScheduler.class);

    private final RankingSnapshotRefreshService refreshService;
    private final UserDailyMetricService metricService;
    private final BatchExecutionService batchExecutionService;
    private final Clock clock;

    @Scheduled(cron = "${app.ranking.refresh-cron:0 */5 * * * *}", zone = "Asia/Seoul")
    public void refresh() {
        LocalDate today = LocalDate.now(clock);
        try {
            batchExecutionService.execute(
                    BatchJobType.DAILY_METRICS,
                    today,
                    () -> metricService.refreshDateForActiveUsers(today));
        } catch (RuntimeException exception) {
            log.error("Daily metrics refresh failed for {}", today, exception);
        }
        try {
            batchExecutionService.execute(BatchJobType.RANKING, today, refreshService::refreshCurrent);
        } catch (RuntimeException exception) {
            log.error("Ranking refresh failed for {}", today, exception);
        }
    }
}
