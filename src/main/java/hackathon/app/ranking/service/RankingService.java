package hackathon.app.ranking.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hackathon.app.auth.domain.AuthSession;
import hackathon.app.auth.infrastructure.AuthSessionJpaRepository;
import hackathon.app.ranking.dto.response.GetRankingResponse;
import hackathon.app.ranking.enums.PeriodType;
import hackathon.app.ranking.enums.RankingType;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RankingService {

    private static final int DEFAULT_RANKING_SIZE = 50;
    private static final int MAX_RANKING_SIZE = 100;
    private static final PeriodType DEFAULT_PERIOD = PeriodType.ALL;

    private final RankingSnapshotRefreshService rankingSnapshotRefreshService;
    private final AuthSessionJpaRepository authSessionRepository;

    public GetRankingResponse getRankings(String sessionId, String type, Long categoryId, String period,
                                          Integer size) {
        RankingType rankingType = parseType(type);
        PeriodType periodType = parsePeriod(period);
        if (categoryId != null && categoryId <= 0) {
            throw new IllegalArgumentException("categoryId: 1 이상이어야 합니다.");
        }
        int pageSize = normalizeSize(size);

        RankingResult result = rankingSnapshotRefreshService.calculateCurrent(
                rankingType, periodType, categoryId);
        List<RankingResult.Entry> topEntries = result.entries().stream().limit(pageSize).toList();
        RankingResult.Entry myEntry = findMyEntry(sessionId, result.entries());

        return GetRankingResponse.of(
                result.rankingDate(), topEntries, myEntry, result.entries().size());
    }

    /** 내 순위는 상위 50명 밖에 있어도 채워야 하므로 별도로 조회한다. 비로그인이면 null 이다. */
    private RankingResult.Entry findMyEntry(String sessionId, List<RankingResult.Entry> entries) {
        Long myUserId = currentUserIdOrNull(sessionId);
        if (myUserId == null) {
            return null;
        }
        return entries.stream()
                .filter(entry -> entry.userId().equals(myUserId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 선택 인증이라 로그인하지 않아도 200 을 내려야 한다.
     *
     * AuthService.requireUser() 는 비로그인일 때 예외를 던지는데, 그 예외를 여기서 잡아도
     * 이미 공유 트랜잭션이 rollback-only 로 표시되어 커밋 시점에 UnexpectedRollbackException(500) 이 난다.
     * 그래서 예외를 쓰지 않고 세션을 직접 조회한다. 유효성 판정은 AuthSession.isUsable() 을 그대로 쓴다.
     */
    private Long currentUserIdOrNull(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return authSessionRepository.findById(sessionId)
                .filter(AuthSession::isUsable)
                .map(AuthSession::getUserId)
                .orElse(null);
    }

    private RankingType parseType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type: 필수 파라미터입니다.");
        }
        try {
            return RankingType.valueOf(type);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("type: 허용되지 않는 값입니다: " + type);
        }
    }

    private PeriodType parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return DEFAULT_PERIOD;
        }
        try {
            return PeriodType.valueOf(period);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("period: 허용되지 않는 값입니다: " + period);
        }
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_RANKING_SIZE;
        }
        if (size < 1 || size > MAX_RANKING_SIZE) {
            throw new IllegalArgumentException("size: 1~" + MAX_RANKING_SIZE + " 범위여야 합니다.");
        }
        return size;
    }
}
