package hackathon.app.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import hackathon.app.domain.puzzle.repository.PuzzlePieceRepository;
import hackathon.app.domain.puzzle.repository.PuzzleRepository;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import hackathon.app.preference.infrastructure.JpaUserPreferenceRepository;
import hackathon.app.ranking.enums.PeriodType;
import hackathon.app.ranking.enums.RankingType;
import hackathon.app.ranking.repository.RankingSnapshotRepository;
import hackathon.app.user.domain.User;
import hackathon.app.user.domain.UserStatus;
import hackathon.app.user.infrastructure.JpaUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class RankingSnapshotRefreshServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate RANKING_DATE = LocalDate.of(2026, 8, 21);

    private final RankingSnapshotRepository snapshotRepository = mock(RankingSnapshotRepository.class);
    private final PuzzlePieceRepository pieceRepository = mock(PuzzlePieceRepository.class);
    private final PuzzleRepository puzzleRepository = mock(PuzzleRepository.class);
    private final ScheduleItemRepository itemRepository = mock(ScheduleItemRepository.class);
    private final JpaUserRepository userRepository = mock(JpaUserRepository.class);
    private final JpaUserPreferenceRepository preferenceRepository = mock(JpaUserPreferenceRepository.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-20T20:00:00Z"), SEOUL);
    private final RankingSnapshotRefreshService service = new RankingSnapshotRefreshService(
            snapshotRepository,
            pieceRepository,
            puzzleRepository,
            itemRepository,
            userRepository,
            preferenceRepository,
            clock);

    @Test
    void calculatesCurrentRankingFromDomainEntitiesWithoutReadingSnapshots() {
        User first = user(1L, "첫째");
        User second = user(2L, "둘째");
        List<ScheduleItemRepository.RankingItemProjection> items = List.of(
                item(101L, 1L, 11L, 3L),
                item(102L, 1L, 11L, 3L),
                item(201L, 2L, 22L, 4L));
        List<PuzzlePieceRepository.RankingActivityProjection> activities = List.of(
                activity(1L, 101L, RANKING_DATE.minusDays(1).atTime(10, 0)),
                activity(1L, 102L, RANKING_DATE.atTime(10, 0)),
                activity(2L, 201L, RANKING_DATE.atTime(11, 0)));
        when(userRepository.findAllByStatus(UserStatus.ACTIVE)).thenReturn(List.of(first, second));
        when(preferenceRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(itemRepository.findRankingItems(anyCollection())).thenReturn(items);
        when(pieceRepository.findRankingActivities(anyCollection())).thenReturn(activities);
        when(puzzleRepository.findCompletedRankingFacts(anyCollection(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        RankingResult result = service.calculateCurrent(RankingType.STREAK, PeriodType.ALL, null);

        assertThat(result.rankingDate()).isEqualTo(RANKING_DATE);
        assertThat(result.entries()).extracting(
                        RankingResult.Entry::rank,
                        RankingResult.Entry::userId,
                        RankingResult.Entry::nickname,
                        entry -> entry.score().toPlainString())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, 1L, "첫째", "2.00"),
                        org.assertj.core.groups.Tuple.tuple(2, 2L, "둘째", "1.00"));
        verifyNoInteractions(snapshotRepository);
    }

    private User user(Long id, String nickname) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getNickname()).thenReturn(nickname);
        return user;
    }

    private ScheduleItemRepository.RankingItemProjection item(
            Long itemId, Long userId, Long scheduleId, Long categoryId) {
        ScheduleItemRepository.RankingItemProjection item =
                mock(ScheduleItemRepository.RankingItemProjection.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getUserId()).thenReturn(userId);
        when(item.getScheduleId()).thenReturn(scheduleId);
        when(item.getCategoryId()).thenReturn(categoryId);
        when(item.getScheduledDate()).thenReturn(RANKING_DATE);
        when(item.getWorkload()).thenReturn(30);
        when(item.getStatus()).thenReturn(ScheduleItemStatus.COMPLETED);
        return item;
    }

    private PuzzlePieceRepository.RankingActivityProjection activity(
            Long userId, Long scheduleItemId, LocalDateTime earnedAt) {
        PuzzlePieceRepository.RankingActivityProjection activity =
                mock(PuzzlePieceRepository.RankingActivityProjection.class);
        when(activity.getUserId()).thenReturn(userId);
        when(activity.getScheduleItemId()).thenReturn(scheduleItemId);
        when(activity.getEarnedAt()).thenReturn(earnedAt);
        return activity;
    }
}
