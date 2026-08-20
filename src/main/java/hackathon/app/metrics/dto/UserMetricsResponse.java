package hackathon.app.metrics.dto;

import hackathon.app.metrics.entity.UserDailyMetric;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public record UserMetricsResponse(
        int plannedItemCount,
        int completedItemCount,
        int puzzlePieceCount,
        BigDecimal achievementRate,
        int consecutiveDays,
        List<DailyMetric> daily
) {
    public record DailyMetric(
            LocalDate date,
            int plannedItemCount,
            int completedItemCount,
            int puzzlePieceCount,
            BigDecimal achievementRate,
            int consecutiveDays
    ) {
        static DailyMetric from(UserDailyMetric metric) {
            return new DailyMetric(
                    metric.getMetricDate(),
                    metric.getPlannedItemCount(),
                    metric.getCompletedItemCount(),
                    metric.getPuzzleCount(),
                    metric.getAchievementRate(),
                    metric.getConsecutiveDays());
        }
    }

    public static UserMetricsResponse from(List<UserDailyMetric> metrics, LocalDate to) {
        int planned = metrics.stream().mapToInt(UserDailyMetric::getPlannedItemCount).sum();
        int completed = metrics.stream().mapToInt(UserDailyMetric::getCompletedItemCount).sum();
        int pieces = metrics.stream().mapToInt(UserDailyMetric::getPuzzleCount).sum();
        BigDecimal rate = planned == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(completed)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(planned), 2, RoundingMode.HALF_UP);
        int consecutiveDays = metrics.stream()
                .filter(metric -> metric.getMetricDate().equals(to))
                .mapToInt(UserDailyMetric::getConsecutiveDays)
                .findFirst()
                .orElse(0);
        return new UserMetricsResponse(
                planned,
                completed,
                pieces,
                rate,
                consecutiveDays,
                metrics.stream().map(DailyMetric::from).toList());
    }
}
