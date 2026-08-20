package hackathon.app.ranking.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import hackathon.app.ranking.enums.PeriodType;
import hackathon.app.ranking.enums.RankingScope;
import hackathon.app.ranking.enums.RankingType;
import jakarta.persistence.*;
import lombok.Getter;

/**
 * 주기적으로 다시 계산하는 랭킹 스냅샷.
 * 운영 스키마는 버전 관리되는 migration SQL 과 일치해야 한다.
 * user_id / category_id 는 FK 지만 연관관계를 매핑하지 않고 Long 값만 들고 있다
 * (User 는 다른 사람의 엔티티이고 Category 엔티티는 아직 이 레포에 없다).
 */
@Getter
@Entity
@Table(name = "ranking_snapshots")
public class RankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ranking_date", nullable = false)
    private LocalDate rankingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "ranking_type", nullable = false)
    private RankingType rankingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false)
    private PeriodType periodType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private RankingScope scope;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(name = "score", nullable = false, precision = 14, scale = 2)
    private BigDecimal score;

    @Column(name = "puzzle_count", nullable = false)
    private int puzzleCount;

    @Column(name = "active_days", nullable = false)
    private int activeDays;

    @Column(name = "achievement_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal achievementRate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected RankingSnapshot() {
        // JPA 전용 기본 생성자
    }

    public static RankingSnapshot create(LocalDate rankingDate,
                                         RankingType rankingType,
                                         PeriodType periodType,
                                         RankingScope scope,
                                         Long categoryId,
                                         Long userId,
                                         int rankNo,
                                         BigDecimal score,
                                         int puzzleCount,
                                         int activeDays,
                                         BigDecimal achievementRate,
                                         LocalDateTime createdAt) {
        RankingSnapshot snapshot = new RankingSnapshot();
        snapshot.rankingDate = rankingDate;
        snapshot.rankingType = rankingType;
        snapshot.periodType = periodType;
        snapshot.scope = scope;
        snapshot.categoryId = categoryId;
        snapshot.userId = userId;
        snapshot.rankNo = rankNo;
        snapshot.score = score;
        snapshot.puzzleCount = puzzleCount;
        snapshot.activeDays = activeDays;
        snapshot.achievementRate = achievementRate;
        snapshot.createdAt = createdAt;
        return snapshot;
    }
}
