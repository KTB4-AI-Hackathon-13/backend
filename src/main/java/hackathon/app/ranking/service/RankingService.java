package hackathon.app.ranking.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hackathon.app.auth.domain.AuthSession;
import hackathon.app.auth.infrastructure.AuthSessionJpaRepository;
import hackathon.app.ranking.dto.response.GetRankingResponse;
import hackathon.app.ranking.entity.RankingSnapshot;
import hackathon.app.ranking.enums.PeriodType;
import hackathon.app.ranking.enums.RankingScope;
import hackathon.app.ranking.enums.RankingType;
import hackathon.app.ranking.repository.RankingSnapshotRepository;
import hackathon.app.user.domain.User;
import hackathon.app.user.infrastructure.JpaUserRepository;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RankingService {

    /** 상위 몇 명까지 내려줄지. 요청으로 조절하지 않고 고정한다. */
    private static final int RANKING_SIZE = 50;
    private static final int FIRST_PAGE = 0;
    private static final PeriodType DEFAULT_PERIOD = PeriodType.WEEKLY; // 디폴트는 주간랭킹

    private final RankingSnapshotRepository rankingSnapshotRepository;
    private final AuthSessionJpaRepository authSessionRepository;
    private final JpaUserRepository userRepository;

    public GetRankingResponse getRankings(String sessionId, String type, Long categoryId, String period) {
        RankingType rankingType = parseType(type);
        PeriodType periodType = parsePeriod(period);
        RankingScope scope = (categoryId == null) ? RankingScope.OVERALL : RankingScope.CATEGORY;

        LocalDate rankingDate =
                rankingSnapshotRepository.findLatestRankingDate(rankingType, periodType, scope, categoryId);
        if (rankingDate == null) {
            return GetRankingResponse.empty();
        }

        List<RankingSnapshot> snapshots = rankingSnapshotRepository.findTopRankings(
                rankingDate, rankingType, periodType, scope, categoryId,
                PageRequest.of(FIRST_PAGE, RANKING_SIZE));
        RankingSnapshot mySnapshot =
                findMySnapshot(sessionId, rankingDate, rankingType, periodType, scope, categoryId);
        long participants = (mySnapshot == null)
                ? 0
                : rankingSnapshotRepository.countParticipants(rankingDate, rankingType, periodType, scope, categoryId);

        return GetRankingResponse.of(rankingDate, snapshots, findNicknames(snapshots), mySnapshot, participants);
    }

    /** 내 순위는 상위 50명 밖에 있어도 채워야 하므로 별도로 조회한다. 비로그인이면 null 이다. */
    private RankingSnapshot findMySnapshot(String sessionId, LocalDate rankingDate, RankingType type,
                                           PeriodType period, RankingScope scope, Long categoryId) {
        Long myUserId = currentUserIdOrNull(sessionId);
        if (myUserId == null) {
            return null;
        }
        return rankingSnapshotRepository
                .findByUser(rankingDate, type, period, scope, categoryId, myUserId)
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

    /** 반복문 안에서 findById 를 부르면 N+1 이 된다. findAllById 한 번으로 끝낸다. */
    private Map<Long, String> findNicknames(List<RankingSnapshot> snapshots) {
        List<Long> userIds = snapshots.stream().map(RankingSnapshot::getUserId).toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> nicknames = new HashMap<>();
        for (User user : userRepository.findAllById(userIds)) {
            nicknames.put(user.getId(), user.getNickname());
        }
        return nicknames;
    }

    private RankingType parseType(String type) {
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
}
