package hackathon.app.ranking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import hackathon.app.ranking.enums.RankingTier;
import hackathon.app.ranking.service.RankingResult;

/**
 * GET /api/v1/rankings 응답. ApiResponse 로 한 번 더 감싸지므로 실제 JSON 은 {"data": { ... }} 형태다.
 * myRanking 은 값이 없을 때 키를 생략하지 않고 null 로 내보낸다 (프론트가 한 가지만 분기하면 되도록).
 */
public record GetRankingResponse(
        LocalDate rankingDate,
        List<RankingItem> items,
        MyRanking myRanking,
        int participantCount
) {

    public record RankingItem(int rank, Long userId, String nickname, BigDecimal score) {

        public static RankingItem of(RankingResult.Entry entry) {
            return new RankingItem(
                    entry.rank(),
                    entry.userId(),
                    entry.nickname(),
                    entry.score()
            );
        }
    }

    public record MyRanking(int rank, BigDecimal score, RankingTier tier) {

        public static MyRanking of(RankingResult.Entry entry, long participants) {
            return new MyRanking(
                    entry.rank(),
                    entry.score(),
                    RankingTier.of(entry.rank(), participants)
            );
        }
    }

    /**
     * 조회 결과를 한 번에 응답으로 만든다.
     *
     * @param myEntry  로그인하지 않았거나 내 순위가 없으면 null
     * @param participants tier 계산용 전체 참가자 수
     */
    public static GetRankingResponse of(LocalDate rankingDate,
                                        List<RankingResult.Entry> entries,
                                        RankingResult.Entry myEntry,
                                        int participants) {
        List<RankingItem> items = new ArrayList<>();
        for (RankingResult.Entry entry : entries) {
            items.add(RankingItem.of(entry));
        }

        MyRanking myRanking = (myEntry == null) ? null : MyRanking.of(myEntry, participants);
        return new GetRankingResponse(rankingDate, items, myRanking, participants);
    }

    /** 활성 사용자에게 해당 조건의 활동이 아직 없는 상태. */
    public static GetRankingResponse empty() {
        return new GetRankingResponse(null, List.of(), null, 0);
    }
}
