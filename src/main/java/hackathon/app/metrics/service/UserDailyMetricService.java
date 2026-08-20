package hackathon.app.metrics.service;

import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.domain.puzzle.repository.PuzzlePieceRepository;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import hackathon.app.metrics.dto.UserMetricsResponse;
import hackathon.app.metrics.entity.UserDailyMetric;
import hackathon.app.metrics.repository.UserDailyMetricRepository;
import hackathon.app.ranking.service.RankingSnapshotRefreshService;
import hackathon.app.user.domain.UserStatus;
import hackathon.app.user.infrastructure.JpaUserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** user_daily_metrics 재집계와 GET /users/me/metrics 조회를 담당한다. */
@Service
@RequiredArgsConstructor
public class UserDailyMetricService {

    private final UserDailyMetricRepository metricRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final PuzzlePieceRepository puzzlePieceRepository;
    private final JpaUserRepository userRepository;

    @Transactional
    public UserMetricsResponse getMetrics(Long userId, LocalDate from, LocalDate to, Long categoryId) {
        validateRange(from, to);
        if (categoryId != null && categoryId <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "categoryId는 1 이상이어야 합니다.");
        }

        rebuild(userId, from, to, categoryId);
        return UserMetricsResponse.from(metricRepository.findRange(userId, categoryId, from, to), to);
    }

    /** 정기 배치용: 활성 사용자의 전체·사용 카테고리 일별 지표를 갱신한다. */
    @Transactional
    public int refreshDateForActiveUsers(LocalDate targetDate) {
        List<Long> userIds = userRepository.findAllByStatus(UserStatus.ACTIVE).stream()
                .map(hackathon.app.user.domain.User::getId)
                .toList();
        for (Long userId : userIds) {
            List<Long> categoryIds = scheduleItemRepository.findRankingItems(List.of(userId)).stream()
                    .map(ScheduleItemRepository.RankingItemProjection::getCategoryId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            rebuild(userId, targetDate, targetDate, null);
            categoryIds.forEach(categoryId -> rebuild(userId, targetDate, targetDate, categoryId));
        }
        return userIds.size();
    }

    private void rebuild(Long userId, LocalDate from, LocalDate to, Long categoryId) {
        List<ScheduleItemRepository.RankingItemProjection> items =
                scheduleItemRepository.findRankingItems(List.of(userId));
        Map<Long, Long> itemCategories = items.stream()
                .filter(item -> item.getCategoryId() != null)
                .collect(Collectors.toMap(
                        ScheduleItemRepository.RankingItemProjection::getItemId,
                        ScheduleItemRepository.RankingItemProjection::getCategoryId));
        List<Activity> activities = puzzlePieceRepository.findRankingActivities(List.of(userId)).stream()
                .filter(activity -> activity.getEarnedAt() != null)
                .map(activity -> new Activity(
                        activity.getEarnedAt().toLocalDate(),
                        itemCategories.get(activity.getScheduleItemId())))
                .filter(activity -> !activity.date().isAfter(to))
                .toList();

        Predicate<Long> scope = candidate -> categoryId == null || categoryId.equals(candidate);
        NavigableSet<LocalDate> activeDates = activities.stream()
                .filter(activity -> scope.test(activity.categoryId()))
                .map(Activity::date)
                .collect(Collectors.toCollection(TreeSet::new));

        TreeSet<LocalDate> metricDates = items.stream()
                .filter(item -> scope.test(item.getCategoryId()))
                .map(ScheduleItemRepository.RankingItemProjection::getScheduledDate)
                .filter(date -> inRange(date, from, to))
                .collect(Collectors.toCollection(TreeSet::new));
        activities.stream()
                .filter(activity -> scope.test(activity.categoryId()))
                .map(Activity::date)
                .filter(date -> inRange(date, from, to))
                .forEach(metricDates::add);
        // 기간 합계의 consecutiveDays가 항상 to 기준이 되도록 기준일 행을 보장한다.
        metricDates.add(to);

        List<UserDailyMetric> rebuilt = new ArrayList<>();
        for (LocalDate date : metricDates) {
            List<ScheduleItemRepository.RankingItemProjection> dailyItems = items.stream()
                    .filter(item -> scope.test(item.getCategoryId()))
                    .filter(item -> item.getStatus() != ScheduleItemStatus.CANCELLED)
                    .filter(item -> date.equals(item.getScheduledDate()))
                    .toList();
            int completed = (int) dailyItems.stream()
                    .filter(item -> item.getStatus() == ScheduleItemStatus.COMPLETED)
                    .count();
            int plannedMinutes = dailyItems.stream().mapToInt(this::estimatedMinutes).sum();
            int completedMinutes = dailyItems.stream()
                    .filter(item -> item.getStatus() == ScheduleItemStatus.COMPLETED)
                    .mapToInt(this::estimatedMinutes)
                    .sum();
            int pieces = (int) activities.stream()
                    .filter(activity -> scope.test(activity.categoryId()))
                    .filter(activity -> date.equals(activity.date()))
                    .count();
            rebuilt.add(UserDailyMetric.create(
                    userId,
                    categoryId,
                    date,
                    dailyItems.size(),
                    completed,
                    plannedMinutes,
                    completedMinutes,
                    pieces,
                    percentage(completed, dailyItems.size()),
                    Math.toIntExact(RankingSnapshotRefreshService.currentStreak(activeDates, date))));
        }

        metricRepository.deleteRange(userId, categoryId, from, to);
        metricRepository.saveAll(rebuilt);
    }

    private int estimatedMinutes(ScheduleItemRepository.RankingItemProjection item) {
        return item.getWorkload() == null ? 0 : item.getWorkload();
    }

    private BigDecimal percentage(long completed, long planned) {
        if (planned == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(planned), 2, RoundingMode.HALF_UP);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "from과 to는 필수입니다.");
        }
        if (from.isAfter(to)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "from은 to보다 늦을 수 없습니다.");
        }
    }

    private boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }

    private record Activity(LocalDate date, Long categoryId) {
    }
}
