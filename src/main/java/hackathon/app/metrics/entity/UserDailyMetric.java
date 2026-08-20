package hackathon.app.metrics.entity;

import hackathon.app.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자별 하루 실적. category_id=null은 전체 통계를 뜻한다. */
@Entity
@Table(name = "user_daily_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDailyMetric extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "planned_item_count", nullable = false)
    private int plannedItemCount;

    @Column(name = "completed_item_count", nullable = false)
    private int completedItemCount;

    @Column(name = "planned_minutes", nullable = false)
    private int plannedMinutes;

    @Column(name = "completed_minutes", nullable = false)
    private int completedMinutes;

    /** 해당 날 최초 완료로 획득한 퍼즐 조각 수. */
    @Column(name = "puzzle_count", nullable = false)
    private int puzzleCount;

    @Column(name = "achievement_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal achievementRate;

    @Column(name = "consecutive_days", nullable = false)
    private int consecutiveDays;

    public static UserDailyMetric create(Long userId, Long categoryId, LocalDate metricDate,
                                         int plannedItemCount, int completedItemCount,
                                         int plannedMinutes, int completedMinutes,
                                         int puzzleCount, BigDecimal achievementRate,
                                         int consecutiveDays) {
        UserDailyMetric metric = new UserDailyMetric();
        metric.userId = userId;
        metric.categoryId = categoryId;
        metric.metricDate = metricDate;
        metric.plannedItemCount = plannedItemCount;
        metric.completedItemCount = completedItemCount;
        metric.plannedMinutes = plannedMinutes;
        metric.completedMinutes = completedMinutes;
        metric.puzzleCount = puzzleCount;
        metric.achievementRate = achievementRate;
        metric.consecutiveDays = consecutiveDays;
        return metric;
    }
}
