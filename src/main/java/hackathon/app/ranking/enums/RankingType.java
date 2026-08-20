package hackathon.app.ranking.enums;

/** 랭킹 기준. DB ranking_snapshots.ranking_type ENUM 과 값이 일치해야 한다. */
public enum RankingType {
    STREAK,            // 계획을 실천한 일 수
    COMPLETED_PUZZLES  // 완성한 퍼즐 개수
}
