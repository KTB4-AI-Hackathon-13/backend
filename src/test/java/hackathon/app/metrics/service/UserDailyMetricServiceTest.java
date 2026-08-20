package hackathon.app.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hackathon.app.common.error.ApiException;
import hackathon.app.domain.puzzle.repository.PuzzlePieceRepository;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import hackathon.app.metrics.dto.UserMetricsResponse;
import hackathon.app.metrics.entity.UserDailyMetric;
import hackathon.app.metrics.repository.UserDailyMetricRepository;
import hackathon.app.user.infrastructure.JpaUserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDailyMetricServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate FROM = LocalDate.of(2026, 8, 19);
    private static final LocalDate TO = LocalDate.of(2026, 8, 20);

    @Mock UserDailyMetricRepository metricRepository;
    @Mock ScheduleItemRepository scheduleItemRepository;
    @Mock PuzzlePieceRepository puzzlePieceRepository;
    @Mock JpaUserRepository userRepository;

    private UserDailyMetricService service;

    @BeforeEach
    void setUp() {
        service = new UserDailyMetricService(
                metricRepository, scheduleItemRepository, puzzlePieceRepository, userRepository);
    }

    @Test
    @DisplayName("카테고리 기간 통계를 작업·조각 원본에서 재집계한다")
    void getMetrics_rebuildsCategoryMetrics() {
        when(scheduleItemRepository.findRankingItems(List.of(USER_ID))).thenReturn(List.of(
                item(101L, 10L, FROM, 2, ScheduleItemStatus.COMPLETED),
                item(102L, 10L, TO, 3, ScheduleItemStatus.TODO),
                item(103L, 20L, TO, 4, ScheduleItemStatus.COMPLETED)));
        when(puzzlePieceRepository.findRankingActivities(List.of(USER_ID))).thenReturn(List.of(
                activity(101L, FROM),
                activity(102L, TO),
                activity(103L, TO)));

        AtomicReference<List<UserDailyMetric>> saved = new AtomicReference<>(List.of());
        when(metricRepository.saveAll(any())).thenAnswer(invocation -> {
            List<UserDailyMetric> rows = new ArrayList<>();
            invocation.<Iterable<UserDailyMetric>>getArgument(0).forEach(rows::add);
            saved.set(rows);
            return rows;
        });
        when(metricRepository.findRange(USER_ID, 10L, FROM, TO)).thenAnswer(ignored -> saved.get());

        UserMetricsResponse response = service.getMetrics(USER_ID, FROM, TO, 10L);

        assertThat(response.plannedItemCount()).isEqualTo(2);
        assertThat(response.completedItemCount()).isEqualTo(1);
        assertThat(response.puzzlePieceCount()).isEqualTo(2);
        assertThat(response.achievementRate()).isEqualByComparingTo("50.00");
        assertThat(response.consecutiveDays()).isEqualTo(2);
        assertThat(response.daily()).extracting(UserMetricsResponse.DailyMetric::date)
                .containsExactly(FROM, TO);
        verify(metricRepository).deleteRange(USER_ID, 10L, FROM, TO);
    }

    @Test
    @DisplayName("from이 to보다 늦으면 400 용도의 요청 오류로 거부한다")
    void getMetrics_rejectsReverseRange() {
        assertThatThrownBy(() -> service.getMetrics(USER_ID, TO, FROM, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("from");
    }

    private static Item item(Long itemId, Long categoryId, LocalDate date, Integer workload,
                             ScheduleItemStatus status) {
        return new Item(itemId, USER_ID, 1000L, categoryId, date, workload, status);
    }

    private static Activity activity(Long itemId, LocalDate date) {
        return new Activity(USER_ID, itemId, date.atTime(12, 0));
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

    private record Activity(Long userId, Long scheduleItemId, LocalDateTime earnedAt)
            implements PuzzlePieceRepository.RankingActivityProjection {
        @Override public Long getUserId() { return userId; }
        @Override public Long getScheduleItemId() { return scheduleItemId; }
        @Override public LocalDateTime getEarnedAt() { return earnedAt; }
    }
}
