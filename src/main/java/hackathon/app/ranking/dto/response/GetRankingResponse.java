package hackathon.app.ranking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import hackathon.app.ranking.entity.RankingSnapshot;
import hackathon.app.ranking.enums.RankingTier;

/**
 * GET /api/v1/rankings 응답. ApiResponse 로 한 번 더 감싸지므로 실제 JSON 은 {"data": { ... }} 형태다.
 * myRanking 은 값이 없을 때 키를 생략하지 않고 null 로 내보낸다 (프론트가 한 가지만 분기하면 되도록).
 */
public record GetRankingResponse(
        LocalDate rankingDate,
        List<RankingItem> items,
        MyRanking myRanking
) {

    public record RankingItem(int rank, Long userId, String nickname, BigDecimal score) {

        public static RankingItem of(RankingSnapshot snapshot, String nickname) {
            return new RankingItem(
                    snapshot.getRankNo(),
                    snapshot.getUserId(),
                    nickname,
                    snapshot.getScore()
            );
        }
    }

    public record MyRanking(int rank, BigDecimal score, RankingTier tier) {

        public static MyRanking of(RankingSnapshot snapshot, long participants) {
            return new MyRanking(
                    snapshot.getRankNo(),
                    snapshot.getScore(),
                    RankingTier.of(snapshot.getRankNo(), participants)
            );
        }
    }

    /** 닉네임을 찾지 못한 사용자에게 쓰는 표시값. 랭킹 목록이 통째로 비지 않도록 한다. */
    private static final String UNKNOWN_NICKNAME = "알 수 없음";

    /**
     * 조회 결과를 한 번에 응답으로 만든다.
     *
     * @param mySnapshot  로그인하지 않았거나 내 순위가 없으면 null
     * @param participants tier 계산용 전체 참가자 수
     */
    public static GetRankingResponse of(LocalDate rankingDate,
                                        List<RankingSnapshot> snapshots,
                                        Map<Long, String> nicknames,
                                        RankingSnapshot mySnapshot,
                                        long participants) {
        List<RankingItem> items = new ArrayList<>();
        for (RankingSnapshot snapshot : snapshots) {
            String nickname = nicknames.getOrDefault(snapshot.getUserId(), UNKNOWN_NICKNAME);
            items.add(RankingItem.of(snapshot, nickname));
        }

        MyRanking myRanking = (mySnapshot == null) ? null : MyRanking.of(mySnapshot, participants);
        return new GetRankingResponse(rankingDate, items, myRanking);
    }

    /** 배치가 아직 스냅샷을 적재하지 않은 상태. 에러가 아니라 빈 결과다. */
    public static GetRankingResponse empty() {
        return new GetRankingResponse(null, List.of(), null);
    }
}
