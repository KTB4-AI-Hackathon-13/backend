package hackathon.app.ranking.enums;

/**
 * DB 에 tier 컬럼이 없어 순위 백분위로 계산한다.
 * 기준이 바뀌면 이 파일 하나만 고치면 된다.
 */
public enum RankingTier {
    DIAMOND,
    PLATINUM,
    GOLD,
    SILVER,
    BRONZE;

    private static final int TOP_RANK = 1;
    private static final double PERCENT_BASE = 100;
    private static final double DIAMOND_PERCENT = 5;
    private static final double PLATINUM_PERCENT = 15;
    private static final double GOLD_PERCENT = 30;
    private static final double SILVER_PERCENT = 60;

    public static RankingTier of(int rankNo, long participants) {
        // 참가자가 3명일 때 1등이 33% 라서 GOLD 로 떨어지는 것을 막는다.
        if (rankNo == TOP_RANK) {
            return DIAMOND;
        }
        if (participants <= 0) {
            return BRONZE;
        }

        double topPercent = (double) rankNo / participants * PERCENT_BASE;
        if (topPercent <= DIAMOND_PERCENT) {
            return DIAMOND;
        }
        if (topPercent <= PLATINUM_PERCENT) {
            return PLATINUM;
        }
        if (topPercent <= GOLD_PERCENT) {
            return GOLD;
        }
        if (topPercent <= SILVER_PERCENT) {
            return SILVER;
        }
        return BRONZE;
    }
}
