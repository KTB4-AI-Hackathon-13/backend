package hackathon.app.ranking.service;

import hackathon.app.domain.puzzle.entity.PuzzleStatus;
import hackathon.app.domain.puzzle.repository.PuzzlePieceRepository;
import hackathon.app.domain.puzzle.repository.PuzzleRepository;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * API·ERD에 정의된 전체/카테고리, 기간, 3종 랭킹 스냅샷을 재생성한다.
 *
 * <p>조각은 최초 획득 이력을 사용하므로 작업 상태를 되돌려도 중복 지급되거나
 * 기존 실천 기록이 사라지지 않는다. 카테고리는 조각의 원본 작업 category_id를 사용한다.
 * 완성 퍼즐에 여러 카테고리 작업이 있으면 각 카테고리에서 퍼즐 한 개로 집계한다.</p>
 */
@Service
@RequiredArgsConstructor
public class RankingSnapshotRefreshService {

    private final RankingSnapshotRepository rankingSnapshotRepository;
    private final PuzzlePieceRepository puzzlePieceRepository;
    private final PuzzleRepository puzzleRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final JpaUserRepository userRepository;
    private final JpaUserPreferenceRepository preferenceRepository;
    private final Clock clock;
    private final AtomicReference<LocalDate> lastRefreshDate = new AtomicReference<>();

    /**
     * 랭킹 조회용 실시간 계산.
     *
     * <p>조회 API가 파생 테이블인 ranking_snapshots의 스키마나 배치 성공 여부에 의존하지 않도록
     * 현재 User, ScheduleItem, Puzzle, PuzzlePiece 엔티티 데이터만 읽어 결과를 만든다.</p>
     */
    @Transactional(readOnly = true)
    public RankingResult calculateCurrent(RankingType type, PeriodType period, Long categoryId) {
        LocalDate rankingDate = LocalDate.now(clock);
        List<User> eligibleUsers = eligibleUsers();
        List<Long> userIds = eligibleUsers.stream().map(User::getId).toList();

        List<ItemFact> items = itemFacts(userIds);
        Map<Long, Long> itemCategories = items.stream()
                .filter(item -> item.categoryId() != null)
                .collect(Collectors.toMap(ItemFact::itemId, ItemFact::categoryId));
        Map<Long, Set<Long>> scheduleCategories = scheduleCategories(items);
        List<ActivityFact> activities = activityFacts(userIds, itemCategories, rankingDate);
        List<CompletedPuzzleFact> completedPuzzles = completedPuzzleFacts(
                userIds, scheduleCategories, rankingDate);

        ScopeKey scope = categoryId == null
                ? new ScopeKey(RankingScope.OVERALL, null)
                : new ScopeKey(RankingScope.CATEGORY, categoryId);
        RankingPeriodWindow window = RankingPeriodWindow.of(period, rankingDate);
        Map<Long, UserStats> stats = calculateStats(
                eligibleUsers, activities, completedPuzzles, items, scope, window);
        List<ScoreEntry> scoreEntries = rankedEntries(type, eligibleUsers, stats);

        List<RankingResult.Entry> entries = new ArrayList<>();
        Long previousScore = null;
        int rank = 0;
        for (int index = 0; index < scoreEntries.size(); index++) {
            ScoreEntry entry = scoreEntries.get(index);
            if (previousScore == null || previousScore.longValue() != entry.score()) {
                rank = index + 1;
            }
            previousScore = entry.score();
            entries.add(new RankingResult.Entry(
                    rank,
                    entry.user().getId(),
                    entry.user().getNickname(),
                    BigDecimal.valueOf(entry.score()).setScale(2)));
        }
        return new RankingResult(rankingDate, entries);
    }

    /** 첫 조회에서 오늘 스냅샷이 없을 때 한 번만 즉시 생성한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized void refreshIfStale() {
        LocalDate today = LocalDate.now(clock);
        if (today.equals(lastRefreshDate.get())) {
            return;
        }
        if (today.equals(rankingSnapshotRepository.findLatestSnapshotDate())
                && rankingSnapshotRepository
                        .existsByRankingDateAndRankingTypeAndPeriodTypeAndScopeAndCategoryIdIsNull(
                                today,
                                RankingType.PUZZLE_PIECES,
                                PeriodType.ALL,
                                RankingScope.OVERALL)) {
            lastRefreshDate.set(today);
            return;
        }
        rebuild(today);
    }

    /** 주기 배치에서 당일 집계 결과를 갱신한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized int refreshCurrent() {
        return rebuild(LocalDate.now(clock));
    }

    private int rebuild(LocalDate rankingDate) {
        List<User> eligibleUsers = eligibleUsers();
        List<Long> userIds = eligibleUsers.stream().map(User::getId).toList();

        List<ItemFact> items = itemFacts(userIds);
        Map<Long, Long> itemCategories = items.stream()
                .filter(item -> item.categoryId() != null)
                .collect(Collectors.toMap(ItemFact::itemId, ItemFact::categoryId));
        Map<Long, Set<Long>> scheduleCategories = scheduleCategories(items);
        List<ActivityFact> activities = activityFacts(userIds, itemCategories, rankingDate);
        List<CompletedPuzzleFact> completedPuzzles = completedPuzzleFacts(
                userIds, scheduleCategories, rankingDate);

        rankingSnapshotRepository.deleteByRankingDate(rankingDate);

        List<ScopeKey> scopes = scopes(items);
        List<RankingSnapshot> snapshots = new ArrayList<>();
        for (PeriodType period : PeriodType.values()) {
            RankingPeriodWindow window = RankingPeriodWindow.of(period, rankingDate);
            for (ScopeKey scope : scopes) {
                Map<Long, UserStats> stats = calculateStats(
                        eligibleUsers, activities, completedPuzzles, items, scope, window);
                for (RankingType type : RankingType.values()) {
                    snapshots.addAll(buildSnapshots(
                            type, period, scope, eligibleUsers, stats, rankingDate));
                }
            }
        }

        rankingSnapshotRepository.saveAll(snapshots);
        lastRefreshDate.set(rankingDate);
        return eligibleUsers.size();
    }

    private List<User> eligibleUsers() {
        List<User> activeUsers = userRepository.findAllByStatus(UserStatus.ACTIVE);
        if (activeUsers.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = activeUsers.stream().map(User::getId).toList();
        Map<Long, UserPreference> preferences = preferenceRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserPreference::getUserId, preference -> preference));

        // 가입 시점의 과거 데이터처럼 설정 행이 없으면 기본값(참여=true)으로 취급한다.
        return activeUsers.stream()
                .filter(user -> {
                    UserPreference preference = preferences.get(user.getId());
                    return preference == null || preference.isRankingParticipationEnabled();
                })
                .toList();
    }

    private List<ItemFact> itemFacts(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return scheduleItemRepository.findRankingItems(userIds).stream()
                .map(item -> new ItemFact(
                        item.getItemId(), item.getUserId(), item.getScheduleId(), item.getCategoryId(),
                        item.getScheduledDate(), item.getWorkload(), item.getStatus()))
                .toList();
    }

    private List<ActivityFact> activityFacts(List<Long> userIds, Map<Long, Long> itemCategories,
                                             LocalDate rankingDate) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        List<ActivityFact> result = new ArrayList<>();
        for (PuzzlePieceRepository.RankingActivityProjection activity
                : puzzlePieceRepository.findRankingActivities(userIds)) {
            LocalDateTime earnedAt = activity.getEarnedAt();
            if (earnedAt == null || earnedAt.toLocalDate().isAfter(rankingDate)) {
                continue;
            }
            result.add(new ActivityFact(
                    activity.getUserId(),
                    itemCategories.get(activity.getScheduleItemId()),
                    earnedAt.toLocalDate()));
        }
        return result;
    }

    private List<CompletedPuzzleFact> completedPuzzleFacts(List<Long> userIds,
                                                           Map<Long, Set<Long>> scheduleCategories,
                                                           LocalDate rankingDate) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return puzzleRepository.findCompletedRankingFacts(userIds, PuzzleStatus.COMPLETED).stream()
                .filter(puzzle -> puzzle.getCompletedAt() != null)
                .filter(puzzle -> !puzzle.getCompletedAt().toLocalDate().isAfter(rankingDate))
                .map(puzzle -> new CompletedPuzzleFact(
                        puzzle.getPuzzleId(),
                        puzzle.getUserId(),
                        puzzle.getCompletedAt().toLocalDate(),
                        scheduleCategories.getOrDefault(puzzle.getScheduleId(), Set.of())))
                .toList();
    }

    private Map<Long, Set<Long>> scheduleCategories(List<ItemFact> items) {
        Map<Long, Set<Long>> result = new HashMap<>();
        for (ItemFact item : items) {
            if (item.scheduleId() == null || item.categoryId() == null
                    || item.status() == ScheduleItemStatus.CANCELLED) {
                continue;
            }
            result.computeIfAbsent(item.scheduleId(), ignored -> new HashSet<>()).add(item.categoryId());
        }
        return result;
    }

    private List<ScopeKey> scopes(List<ItemFact> items) {
        Set<Long> categoryIds = items.stream()
                .map(ItemFact::categoryId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
        List<ScopeKey> scopes = new ArrayList<>();
        scopes.add(new ScopeKey(RankingScope.OVERALL, null));
        categoryIds.forEach(categoryId -> scopes.add(new ScopeKey(RankingScope.CATEGORY, categoryId)));
        return scopes;
    }

    private Map<Long, UserStats> calculateStats(List<User> users,
                                                List<ActivityFact> activities,
                                                List<CompletedPuzzleFact> completedPuzzles,
                                                List<ItemFact> items,
                                                ScopeKey scope,
                                                RankingPeriodWindow window) {
        Map<Long, UserStats> result = new HashMap<>();
        for (User user : users) {
            Long userId = user.getId();
            NavigableSet<LocalDate> activityDates = activities.stream()
                    .filter(activity -> activity.userId().equals(userId))
                    .filter(activity -> matchesScope(activity.categoryId(), scope))
                    .map(ActivityFact::earnedDate)
                    .filter(window::contains)
                    .collect(Collectors.toCollection(TreeSet::new));
            long puzzlePieces = activities.stream()
                    .filter(activity -> activity.userId().equals(userId))
                    .filter(activity -> matchesScope(activity.categoryId(), scope))
                    .filter(activity -> window.contains(activity.earnedDate()))
                    .count();
            long completedPuzzleCount = completedPuzzles.stream()
                    .filter(puzzle -> puzzle.userId().equals(userId))
                    .filter(puzzle -> window.contains(puzzle.completedDate()))
                    .filter(puzzle -> scope.scope() == RankingScope.OVERALL
                            || puzzle.categoryIds().contains(scope.categoryId()))
                    .count();

            List<ItemFact> periodItems = items.stream()
                    .filter(item -> item.userId().equals(userId))
                    .filter(item -> item.status() != ScheduleItemStatus.CANCELLED)
                    .filter(item -> matchesScope(item.categoryId(), scope))
                    .filter(item -> window.contains(item.scheduledDate()))
                    .toList();
            long completedItems = periodItems.stream()
                    .filter(item -> item.status() == ScheduleItemStatus.COMPLETED)
                    .count();
            BigDecimal achievementRate = percentage(completedItems, periodItems.size());

            result.put(userId, new UserStats(
                    currentStreak(activityDates, window.to(), window.from()),
                    puzzlePieces,
                    completedPuzzleCount,
                    activityDates.size(),
                    achievementRate));
        }
        return result;
    }

    private boolean matchesScope(Long categoryId, ScopeKey scope) {
        return scope.scope() == RankingScope.OVERALL || scope.categoryId().equals(categoryId);
    }

    private List<RankingSnapshot> buildSnapshots(RankingType type,
                                                 PeriodType period,
                                                 ScopeKey scope,
                                                 List<User> users,
                                                 Map<Long, UserStats> stats,
                                                 LocalDate rankingDate) {
        List<ScoreEntry> entries = rankedEntries(type, users, stats);

        List<RankingSnapshot> snapshots = new ArrayList<>();
        Long previousScore = null;
        int rank = 0;
        LocalDateTime createdAt = LocalDateTime.now(clock);
        for (int index = 0; index < entries.size(); index++) {
            ScoreEntry entry = entries.get(index);
            if (previousScore == null || previousScore.longValue() != entry.score()) {
                rank = index + 1;
            }
            previousScore = entry.score();

            UserStats userStats = entry.stats();
            snapshots.add(RankingSnapshot.create(
                    rankingDate,
                    type,
                    period,
                    scope.scope(),
                    scope.categoryId(),
                    entry.user().getId(),
                    rank,
                    BigDecimal.valueOf(entry.score()),
                    Math.toIntExact(userStats.completedPuzzles()),
                    userStats.activeDays(),
                    userStats.achievementRate(),
                    createdAt));
        }
        return snapshots;
    }

    private List<ScoreEntry> rankedEntries(RankingType type,
                                           List<User> users,
                                           Map<Long, UserStats> stats) {
        return users.stream()
                .map(user -> {
                    UserStats userStats = stats.get(user.getId());
                    return new ScoreEntry(user, userStats, score(type, userStats));
                })
                .filter(entry -> entry.score() > 0)
                .sorted(Comparator.comparingLong(ScoreEntry::score).reversed()
                        .thenComparing(entry -> entry.user().getId()))
                .toList();
    }

    private long score(RankingType type, UserStats stats) {
        return switch (type) {
            case STREAK -> stats.streak();
            case COMPLETED_PUZZLES -> stats.completedPuzzles();
            case PUZZLE_PIECES -> stats.puzzlePieces();
        };
    }

    private static BigDecimal percentage(long completed, long planned) {
        if (planned == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(planned), 2, RoundingMode.HALF_UP);
    }

    public static long currentStreak(NavigableSet<LocalDate> dates, LocalDate rankingDate) {
        return currentStreak(dates, rankingDate, null);
    }

    public static long currentStreak(NavigableSet<LocalDate> dates, LocalDate rankingDate,
                                     LocalDate periodStart) {
        if (dates == null || dates.isEmpty()) {
            return 0;
        }

        LocalDate latest = dates.floor(rankingDate);
        if (latest == null || latest.isBefore(rankingDate.minusDays(1))
                || (periodStart != null && latest.isBefore(periodStart))) {
            return 0;
        }

        long streak = 0;
        LocalDate expected = latest;
        while ((periodStart == null || !expected.isBefore(periodStart)) && dates.contains(expected)) {
            streak++;
            expected = expected.minusDays(1);
        }
        return streak;
    }

    private record ItemFact(Long itemId, Long userId, Long scheduleId, Long categoryId,
                            LocalDate scheduledDate, Integer estimatedMinutes, ScheduleItemStatus status) {
    }

    private record ActivityFact(Long userId, Long categoryId, LocalDate earnedDate) {
    }

    private record CompletedPuzzleFact(Long puzzleId, Long userId, LocalDate completedDate,
                                       Set<Long> categoryIds) {
    }

    private record ScopeKey(RankingScope scope, Long categoryId) {
    }

    private record UserStats(long streak, long puzzlePieces, long completedPuzzles,
                             int activeDays, BigDecimal achievementRate) {
    }

    private record ScoreEntry(User user, UserStats stats, long score) {
    }
}
