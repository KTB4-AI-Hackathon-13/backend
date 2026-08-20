package hackathon.app.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hackathon.app.auth.infrastructure.AuthSessionJpaRepository;
import hackathon.app.ranking.dto.response.GetRankingResponse;
import hackathon.app.ranking.enums.PeriodType;
import hackathon.app.ranking.enums.RankingScope;
import hackathon.app.ranking.enums.RankingType;
import hackathon.app.ranking.repository.RankingSnapshotRepository;
import hackathon.app.user.infrastructure.JpaUserRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock RankingSnapshotRepository rankingSnapshotRepository;
    @Mock RankingSnapshotRefreshService refreshService;
    @Mock AuthSessionJpaRepository authSessionRepository;
    @Mock JpaUserRepository userRepository;

    private RankingService service;

    @BeforeEach
    void setUp() {
        service = new RankingService(
                rankingSnapshotRepository,
                refreshService,
                authSessionRepository,
                userRepository);
    }

    @Test
    @DisplayName("기간 생략 시 ALL 전체 랭킹을 조회하고 당일 스냅샷을 먼저 보장한다")
    void getRankings_usesDefaults() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        when(rankingSnapshotRepository.findLatestRankingDate(
                RankingType.STREAK, PeriodType.ALL, RankingScope.OVERALL, null)).thenReturn(date);
        when(rankingSnapshotRepository.findTopRankings(
                org.mockito.ArgumentMatchers.eq(date),
                org.mockito.ArgumentMatchers.eq(RankingType.STREAK),
                org.mockito.ArgumentMatchers.eq(PeriodType.ALL),
                org.mockito.ArgumentMatchers.eq(RankingScope.OVERALL),
                isNull(), any(Pageable.class))).thenReturn(List.of());

        GetRankingResponse response = service.getRankings(null, "STREAK", null, null, null);

        assertThat(response.rankingDate()).isEqualTo(date);
        assertThat(response.items()).isEmpty();
        assertThat(response.myRanking()).isNull();
        verify(refreshService).refreshIfStale();
    }

    @Test
    @DisplayName("기간·카테고리·size 조건을 스냅샷 조회에 그대로 적용한다")
    void getRankings_supportsPeriodCategoryAndSize() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        when(rankingSnapshotRepository.findLatestRankingDate(
                RankingType.PUZZLE_PIECES, PeriodType.WEEKLY, RankingScope.CATEGORY, 3L))
                .thenReturn(date);
        when(rankingSnapshotRepository.findTopRankings(
                org.mockito.ArgumentMatchers.eq(date),
                org.mockito.ArgumentMatchers.eq(RankingType.PUZZLE_PIECES),
                org.mockito.ArgumentMatchers.eq(PeriodType.WEEKLY),
                org.mockito.ArgumentMatchers.eq(RankingScope.CATEGORY),
                org.mockito.ArgumentMatchers.eq(3L), any(Pageable.class))).thenReturn(List.of());

        GetRankingResponse response = service.getRankings(
                null, "PUZZLE_PIECES", 3L, "WEEKLY", 10);

        assertThat(response.rankingDate()).isEqualTo(date);
        verify(rankingSnapshotRepository).findTopRankings(
                org.mockito.ArgumentMatchers.eq(date),
                org.mockito.ArgumentMatchers.eq(RankingType.PUZZLE_PIECES),
                org.mockito.ArgumentMatchers.eq(PeriodType.WEEKLY),
                org.mockito.ArgumentMatchers.eq(RankingScope.CATEGORY),
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageSize() == 10));
    }

    @Test
    @DisplayName("size 허용 범위를 벗어나면 거부한다")
    void getRankings_rejectsInvalidSize() {
        assertThatThrownBy(() -> service.getRankings(null, "STREAK", null, "ALL", 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }
}
