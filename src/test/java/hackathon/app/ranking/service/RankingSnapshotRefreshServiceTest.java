package hackathon.app.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hackathon.app.domain.puzzle.entity.PuzzleStatus;
import hackathon.app.domain.puzzle.repository.PuzzlePieceRepository;
import hackathon.app.domain.puzzle.repository.PuzzleRepository;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.preference.domain.UserPreference;
import hackathon.app.preference.infrastructure.JpaUserPreferenceRepository;
import hackathon.app.ranking.entity.RankingSnapshot;
import hackathon.app.ranking.enums.PeriodType;
import hackathon.app.ranking.enums.RankingScope;
import hackathon.app.ranking.enums.RankingType;
import hackathon.app.ranking.repository.RankingSnapshotRepository;
import hackathon.app.user.domain.User;
import hackathon.app.user.domain.UserStatus;
import hackathon.app.user.infrastructure.JpaUserRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RankingSnapshotRefreshServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Mock RankingSnapshotRepository rankingSnapshotRepository;
    @Mock PuzzlePieceRepository puzzlePieceRepository;
    @Mock PuzzleRepository puzzleRepository;
    @Mock ScheduleItemRepository scheduleItemRepository;
    @Mock JpaUserRepository userRepository;
    @Mock JpaUserPreferenceRepository preferenceRepository;

    private RankingSnapshotRefreshService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T03:00:00Z"), SEOUL);
        service = new RankingSnapshotRefreshService(
                rankingSnapshotRepository,
                puzzlePieceRepository,
                puzzleRepository,
                scheduleItemRepository,
                userRepository,
                preferenceRepository,
                clock);
    }

    @Test
    @DisplayName("참여 회원만 3종·5개 기간으로 집계하고 공동 순위를 적용한다")
    void refreshCurrent_buildsFullRankings() throws Exception {
        List<User> users = List.of(user(1L), user(2L), user(3L), user(4L), user(5L));
        UserPreference optedOut = UserPreference.createDefault(5L);
        optedOut.update(null, null, null, null, null, false, null, null, null);

        when(userRepository.findAllByStatus(UserStatus.ACTIVE)).thenReturn(users);
        when(preferenceRepository.findAllById(any())).thenReturn(List.of(optedOut));
        when(puzzlePieceRepository.findRankingActivities(any())).thenReturn(List.of(
                activity(1L, 101L, TODAY.minusDays(2)), activity(1L, 102L, TODAY.minusDays(1)), activity(1L, 103L, TODAY),
                activity(2L, 201L, TODAY.minusDays(1)), activity(2L, 202L, TODAY),
                activity(3L, 301L, TODAY.minusDays(1)), activity(3L, 302L, TODAY),
                activity(4L, 401L, TODAY),
                activity(5L, 501L, TODAY.minusDays(4)), activity(5L, 502L, TODAY.minusDays(3)),
                activity(5L, 503L, TODAY.minusDays(2)), activity(5L, 504L, TODAY.minusDays(1)),
                activity(5L, 505L, TODAY)));
        when(scheduleItemRepository.findRankingItems(any())).thenReturn(List.of());
        when(puzzleRepository.findCompletedRankingFacts(
                any(), org.mockito.ArgumentMatchers.eq(PuzzleStatus.COMPLETED)))
                .thenReturn(List.of(
                        completedPuzzle(11L, 1001L, 1L),
                        completedPuzzle(21L, 2001L, 2L), completedPuzzle(22L, 2002L, 2L),
                        completedPuzzle(31L, 3001L, 3L), completedPuzzle(32L, 3002L, 3L),
                        completedPuzzle(41L, 4001L, 4L),
                        completedPuzzle(51L, 5001L, 5L)));

        service.refreshCurrent();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<RankingSnapshot>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(rankingSnapshotRepository).saveAll(captor.capture());
        List<RankingSnapshot> saved = new ArrayList<>();
        captor.getValue().forEach(saved::add);

        assertThat(saved).hasSize(60).noneMatch(snapshot -> snapshot.getUserId().equals(5L));
        assertThat(saved).allSatisfy(snapshot -> assertThat(snapshot.getRankingDate()).isEqualTo(TODAY));

        List<RankingSnapshot> allOverall = saved.stream()
                .filter(snapshot -> snapshot.getPeriodType() == PeriodType.ALL)
                .filter(snapshot -> snapshot.getScope() == RankingScope.OVERALL)
                .toList();
        Map<Long, RankingSnapshot> streaks = byUser(allOverall, RankingType.STREAK);
        assertThat(streaks.get(1L).getRankNo()).isEqualTo(1);
        assertThat(streaks.get(1L).getScore()).isEqualByComparingTo("3");
        assertThat(streaks.get(2L).getRankNo()).isEqualTo(2);
        assertThat(streaks.get(3L).getRankNo()).isEqualTo(2);
        assertThat(streaks.get(4L).getRankNo()).isEqualTo(4);

        Map<Long, RankingSnapshot> puzzles = byUser(allOverall, RankingType.COMPLETED_PUZZLES);
        assertThat(puzzles.get(2L).getRankNo()).isEqualTo(1);
        assertThat(puzzles.get(3L).getRankNo()).isEqualTo(1);
        assertThat(puzzles.get(1L).getRankNo()).isEqualTo(3);
        assertThat(puzzles.get(4L).getRankNo()).isEqualTo(3);

        Map<Long, RankingSnapshot> pieces = byUser(allOverall, RankingType.PUZZLE_PIECES);
        assertThat(pieces.get(1L).getScore()).isEqualByComparingTo("3");
        assertThat(pieces.get(2L).getRankNo()).isEqualTo(2);
        assertThat(pieces.get(3L).getRankNo()).isEqualTo(2);

        verify(rankingSnapshotRepository).deleteByRankingDate(TODAY);
    }

    @Test
    @DisplayName("연속 실천: 오늘 기록이 없어도 어제까지의 연속 기록을 유지한다")
    void currentStreak_keepsYesterdayStreak() {
        TreeSet<LocalDate> dates = new TreeSet<>(List.of(
                TODAY.minusDays(3), TODAY.minusDays(2), TODAY.minusDays(1)));

        assertThat(RankingSnapshotRefreshService.currentStreak(dates, TODAY)).isEqualTo(3);
    }

    @Test
    @DisplayName("연속 실천: 이틀 이상 활동이 없으면 현재 연속 기록은 0이다")
    void currentStreak_expiresAfterGap() {
        TreeSet<LocalDate> dates = new TreeSet<>(List.of(TODAY.minusDays(3), TODAY.minusDays(2)));

        assertThat(RankingSnapshotRefreshService.currentStreak(dates, TODAY)).isZero();
    }

    @Test
    @DisplayName("카테고리 랭킹은 원본 작업을 기준으로 조각·완성 퍼즐을 집계한다")
    void refreshCurrent_buildsCategoryRankings() throws Exception {
        User user = user(1L);
        when(userRepository.findAllByStatus(UserStatus.ACTIVE)).thenReturn(List.of(user));
        when(preferenceRepository.findAllById(any())).thenReturn(List.of());
        when(scheduleItemRepository.findRankingItems(any())).thenReturn(List.of(
                item(101L, 1001L, 10L),
                item(102L, 1001L, 20L)));
        when(puzzlePieceRepository.findRankingActivities(any())).thenReturn(List.of(
                activity(1L, 101L, TODAY),
                activity(1L, 102L, TODAY)));
        when(puzzleRepository.findCompletedRankingFacts(any(),
                org.mockito.ArgumentMatchers.eq(PuzzleStatus.COMPLETED)))
                .thenReturn(List.of(completedPuzzle(11L, 1001L, 1L)));

        service.refreshCurrent();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<RankingSnapshot>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(rankingSnapshotRepository).saveAll(captor.capture());
        List<RankingSnapshot> categoryTenAll = new ArrayList<>();
        captor.getValue().forEach(snapshot -> {
            if (snapshot.getScope() == RankingScope.CATEGORY
                    && Long.valueOf(10L).equals(snapshot.getCategoryId())
                    && snapshot.getPeriodType() == PeriodType.ALL) {
                categoryTenAll.add(snapshot);
            }
        });

        assertThat(categoryTenAll).hasSize(3);
        assertThat(byUser(categoryTenAll, RankingType.STREAK).get(1L).getScore())
                .isEqualByComparingTo("1");
        assertThat(byUser(categoryTenAll, RankingType.PUZZLE_PIECES).get(1L).getScore())
                .isEqualByComparingTo("1");
        assertThat(byUser(categoryTenAll, RankingType.COMPLETED_PUZZLES).get(1L).getScore())
                .isEqualByComparingTo("1");
        assertThat(categoryTenAll).allSatisfy(snapshot ->
                assertThat(snapshot.getAchievementRate()).isEqualByComparingTo("100.00"));
    }

    private static Map<Long, RankingSnapshot> byUser(List<RankingSnapshot> snapshots, RankingType type) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.getRankingType() == type)
                .collect(Collectors.toMap(RankingSnapshot::getUserId, Function.identity()));
    }

    private static User user(Long id) throws Exception {
        User user = User.create("user" + id + "@example.com", "hash", "사용자" + id);
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
        return user;
    }

    private static Activity activity(Long userId, Long itemId, LocalDate date) {
        return new Activity(userId, itemId, date.atTime(12, 0));
    }

    private static CompletedPuzzle completedPuzzle(Long puzzleId, Long scheduleId, Long userId) {
        return new CompletedPuzzle(puzzleId, scheduleId, userId, TODAY.atTime(10, 0));
    }

    private static Item item(Long itemId, Long scheduleId, Long categoryId) {
        return new Item(itemId, 1L, scheduleId, categoryId, TODAY, 1, ScheduleItemStatus.COMPLETED);
    }

    private record Activity(Long userId, Long scheduleItemId, LocalDateTime earnedAt)
            implements PuzzlePieceRepository.RankingActivityProjection {
        @Override public Long getUserId() { return userId; }
        @Override public Long getScheduleItemId() { return scheduleItemId; }
        @Override public LocalDateTime getEarnedAt() { return earnedAt; }
    }

    private record CompletedPuzzle(Long puzzleId, Long scheduleId, Long userId, LocalDateTime completedAt)
            implements PuzzleRepository.CompletedPuzzleRankingProjection {
        @Override public Long getPuzzleId() { return puzzleId; }
        @Override public Long getScheduleId() { return scheduleId; }
        @Override public Long getUserId() { return userId; }
        @Override public LocalDateTime getCompletedAt() { return completedAt; }
    }

    private record Item(Long itemId, Long userId, Long scheduleId, Long categoryId,
                        LocalDate scheduledDate, Integer workload, ScheduleItemStatus status)
            implements ScheduleItemRepository.RankingItemProjection {
        @Override public Long getItemId() { return itemId; }
        @Override public Long getUserId() { return userId; }
        @Override public Long getScheduleId() { return scheduleId; }
        @Override public Long getCategoryId() { return categoryId; }
        @Override public LocalDate getScheduledDate() { return scheduledDate; }
        @Override public Integer getWorkload() { return workload; }
        @Override public ScheduleItemStatus getStatus() { return status; }
    }
}
