package hackathon.app.ranking.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 현재 도메인 엔티티에서 계산한 한 가지 조건의 랭킹 결과. */
public record RankingResult(LocalDate rankingDate, List<Entry> entries) {

    public RankingResult {
        entries = List.copyOf(entries);
    }

    public record Entry(int rank, Long userId, String nickname, BigDecimal score) {
    }
}
