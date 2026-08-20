package hackathon.app.ranking.enums;

/**
 * 랭킹 집계 기간. 요청에 period 가 없으면 WEEKLY 다.
 * DB ranking_snapshots.period_type ENUM 과 값이 일치해야 한다.
 */
public enum PeriodType {
    WEEKLY,
    MONTHLY,
    YEARLY
}
