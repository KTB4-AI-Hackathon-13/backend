package hackathon.app.domain.scheduleitem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hackathon.app.domain.schedule.entity.ChangeAction;
import hackathon.app.domain.schedule.entity.ChangeSource;
import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.schedule.entity.ScheduleStatus;
import hackathon.app.domain.schedule.service.ScheduleChangeLogger;
import hackathon.app.domain.schedule.service.ScheduleService;
import hackathon.app.domain.scheduleitem.dto.ScheduleItemCreateRequest;
import hackathon.app.domain.scheduleitem.dto.ScheduleItemResponse;
import hackathon.app.domain.scheduleitem.dto.ScheduleItemStatusResponse;
import hackathon.app.domain.scheduleitem.dto.ScheduleItemUpdateRequest;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.policy.CategoryChecker;
import hackathon.app.domain.scheduleitem.policy.DailyTaskLimitProvider;
import hackathon.app.domain.scheduleitem.policy.PuzzlePieceAwarder;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleItemServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long SCHEDULE_ID = 101L;
    private static final LocalDate IN_PERIOD = LocalDate.of(2026, 8, 19);

    @Mock ScheduleItemRepository scheduleItemRepository;
    @Mock ScheduleService scheduleService;
    @Mock ScheduleChangeLogger changeLogger;
    @Mock DailyTaskLimitProvider dailyTaskLimitProvider;
    @Mock CategoryChecker categoryChecker;
    @Mock PuzzlePieceAwarder puzzlePieceAwarder;
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-08-19T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @InjectMocks ScheduleItemService service;

    private Schedule schedule;

    @BeforeEach
    void setUp() throws Exception {
        schedule = Schedule.builder()
                .userId(USER_ID).title("8월 알고리즘 공부").status(ScheduleStatus.ACTIVE).source(ChangeSource.AI)
                .startDate(LocalDate.of(2026, 8, 17)).endDate(LocalDate.of(2026, 8, 30))
                .build();
        setId(schedule, SCHEDULE_ID);
    }

    private ScheduleItem item(Long id, LocalDate date, int position) throws Exception {
        ScheduleItem i = ScheduleItem.builder()
                .schedule(schedule).title("작업 " + id).scheduledDate(date).position(position).build();
        setId(i, id);
        return i;
    }

    private static void setId(Object entity, Long id) throws Exception {
        Field f = entity.getClass().getDeclaredField("id");
        f.setAccessible(true);
        f.set(entity, id);
    }

    // ===== 작업 추가 =====

    @Test
    @DisplayName("추가: 기간 안·한도 미만이면 생성, position 은 같은 날짜 다음 순번, CREATE 이력 + 버전 +1")
    void createItem_success() throws Exception {
        when(scheduleService.getOwnedSchedule(USER_ID, SCHEDULE_ID)).thenReturn(schedule);
        when(dailyTaskLimitProvider.maxDailyTasks(USER_ID)).thenReturn(5);
        when(scheduleItemRepository.countUserItemsOnDate(eq(USER_ID), eq(IN_PERIOD), eq(ScheduleItemStatus.CANCELLED),
                eq(-1L))).thenReturn(3L);
        when(scheduleItemRepository.nextPosition(SCHEDULE_ID, IN_PERIOD)).thenReturn(2);
        when(scheduleItemRepository.save(any())).thenAnswer(inv -> {
            ScheduleItem saved = inv.getArgument(0);
            setId(saved, 2002L);
            return saved;
        });

        ScheduleItemResponse res = service.createItem(USER_ID, SCHEDULE_ID,
                new ScheduleItemCreateRequest("새 작업", IN_PERIOD, null, null, null, null, null));

        assertThat(res.id()).isEqualTo(2002L);
        assertThat(res.position()).isEqualTo(2);
        assertThat(res.status()).isEqualTo(ScheduleItemStatus.TODO);
        assertThat(res.workload()).isEqualTo(1);   // 기본값
        assertThat(res.priority()).isEqualTo(3);   // 기본값
        assertThat(schedule.getCurrentVersion()).isEqualTo(2);
        verify(changeLogger).log(eq(SCHEDULE_ID), eq(2002L), eq(USER_ID), eq(ChangeAction.CREATE),
                eq(ChangeSource.USER), eq(2), eq(null), any(), eq(null));
    }

    @Test
    @DisplayName("추가: 스케줄 기간 밖 날짜면 422 DATE_OUTSIDE_SCHEDULE_PERIOD")
    void createItem_outsidePeriod() {
        when(scheduleService.getOwnedSchedule(USER_ID, SCHEDULE_ID)).thenReturn(schedule);

        assertThatThrownBy(() -> service.createItem(USER_ID, SCHEDULE_ID,
                new ScheduleItemCreateRequest("x", LocalDate.of(2026, 9, 1), null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.DATE_OUTSIDE_SCHEDULE_PERIOD);
        verify(scheduleItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("추가: 그 날짜에 이미 한도(5)만큼 있으면 422 MAX_DAILY_TASKS_EXCEEDED")
    void createItem_dailyLimitExceeded() {
        when(scheduleService.getOwnedSchedule(USER_ID, SCHEDULE_ID)).thenReturn(schedule);
        when(dailyTaskLimitProvider.maxDailyTasks(USER_ID)).thenReturn(5);
        when(scheduleItemRepository.countUserItemsOnDate(any(), any(), any(), anyLong())).thenReturn(5L);

        assertThatThrownBy(() -> service.createItem(USER_ID, SCHEDULE_ID,
                new ScheduleItemCreateRequest("x", IN_PERIOD, null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.MAX_DAILY_TASKS_EXCEEDED);
    }

    @Test
    @DisplayName("추가: 존재하지 않는 카테고리면 400 INVALID_REQUEST")
    void createItem_invalidCategory() {
        when(scheduleService.getOwnedSchedule(USER_ID, SCHEDULE_ID)).thenReturn(schedule);
        when(dailyTaskLimitProvider.maxDailyTasks(USER_ID)).thenReturn(5);
        when(scheduleItemRepository.countUserItemsOnDate(any(), any(), any(), anyLong())).thenReturn(0L);
        when(categoryChecker.existsActive(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.createItem(USER_ID, SCHEDULE_ID,
                new ScheduleItemCreateRequest("x", IN_PERIOD, null, 99L, null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // ===== 작업 수정 =====

    @Test
    @DisplayName("수정: 타인 소유 작업이면 403 FORBIDDEN")
    void updateItem_forbidden() throws Exception {
        ScheduleItem it = item(1004L, IN_PERIOD, 0);
        when(scheduleItemRepository.findWithScheduleById(1004L)).thenReturn(Optional.of(it));

        assertThatThrownBy(() -> service.updateItem(OTHER_USER_ID, 1004L,
                new ScheduleItemUpdateRequest("x", null, null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("수정: 없는 작업이면 404 SCHEDULE_ITEM_NOT_FOUND")
    void updateItem_notFound() {
        when(scheduleItemRepository.findWithScheduleById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateItem(USER_ID, 999L,
                new ScheduleItemUpdateRequest("x", null, null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.SCHEDULE_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("수정: 날짜를 옮길 때는 자기 자신을 제외하고 한도를 검사한다")
    void updateItem_moveDate_excludesSelfFromLimit() throws Exception {
        ScheduleItem it = item(1006L, LocalDate.of(2026, 8, 20), 0);
        when(scheduleItemRepository.findWithScheduleById(1006L)).thenReturn(Optional.of(it));
        when(dailyTaskLimitProvider.maxDailyTasks(USER_ID)).thenReturn(5);
        when(scheduleItemRepository.countUserItemsOnDate(USER_ID, LocalDate.of(2026, 8, 25),
                ScheduleItemStatus.CANCELLED, 1006L)).thenReturn(4L);

        ScheduleItemResponse res = service.updateItem(USER_ID, 1006L,
                new ScheduleItemUpdateRequest(null, null, LocalDate.of(2026, 8, 25), null, null, null, null));

        assertThat(res.scheduledDate()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(schedule.getCurrentVersion()).isEqualTo(2);
        verify(changeLogger).log(eq(SCHEDULE_ID), eq(1006L), eq(USER_ID), eq(ChangeAction.UPDATE),
                eq(ChangeSource.USER), eq(2), any(), any(), eq(null));
    }

    @Test
    @DisplayName("수정: 날짜가 그대로면 한도 검사를 하지 않는다")
    void updateItem_sameDate_skipsLimitCheck() throws Exception {
        ScheduleItem it = item(1004L, IN_PERIOD, 0);
        when(scheduleItemRepository.findWithScheduleById(1004L)).thenReturn(Optional.of(it));

        service.updateItem(USER_ID, 1004L,
                new ScheduleItemUpdateRequest("새 제목", null, IN_PERIOD, null, null, null, null));

        verify(dailyTaskLimitProvider, never()).maxDailyTasks(any());
        verify(scheduleItemRepository, never()).countUserItemsOnDate(any(), any(), any(), anyLong());
        assertThat(it.getTitle()).isEqualTo("새 제목");
    }

    @Test
    @DisplayName("수정: 빈 요청이면 400")
    void updateItem_empty() {
        assertThatThrownBy(() -> service.updateItem(USER_ID, 1004L,
                new ScheduleItemUpdateRequest(null, null, null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // ===== 상태 변경 =====

    @Test
    @DisplayName("상태: COMPLETED 로 바꾸면 completedAt 이 찍히고 조각 지급 포트를 호출한다")
    void changeStatus_completed_awardsPiece() throws Exception {
        ScheduleItem it = item(1004L, IN_PERIOD, 0);
        when(scheduleItemRepository.findWithScheduleById(1004L)).thenReturn(Optional.of(it));
        when(puzzlePieceAwarder.awardOnComplete(it)).thenReturn(new PuzzlePieceAwarder.AwardResult(true, 77L));

        ScheduleItemStatusResponse res = service.changeStatus(USER_ID, 1004L, ScheduleItemStatus.COMPLETED);

        assertThat(res.status()).isEqualTo(ScheduleItemStatus.COMPLETED);
        assertThat(res.completedAt()).isNotNull();
        assertThat(res.puzzlePieceAwarded()).isTrue();
        assertThat(res.puzzlePieceId()).isEqualTo(77L);
        // 상태 변경은 변경 이력/버전에 남기지 않는다
        verify(changeLogger, never()).log(any(), any(), any(), any(), any(), anyInt(), any(), any(), any());
        assertThat(schedule.getCurrentVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("상태: COMPLETED 가 아닌 상태로 바꾸면 completedAt 이 지워지고 조각 지급을 호출하지 않는다")
    void changeStatus_notCompleted_noAward() throws Exception {
        ScheduleItem it = item(1004L, IN_PERIOD, 0);
        it.changeStatus(ScheduleItemStatus.COMPLETED, java.time.LocalDateTime.of(2026, 8, 19, 10, 0));
        when(scheduleItemRepository.findWithScheduleById(1004L)).thenReturn(Optional.of(it));

        ScheduleItemStatusResponse res = service.changeStatus(USER_ID, 1004L, ScheduleItemStatus.TODO);

        assertThat(res.status()).isEqualTo(ScheduleItemStatus.TODO);
        assertThat(res.completedAt()).isNull();
        assertThat(res.puzzlePieceAwarded()).isFalse();
        verify(puzzlePieceAwarder, never()).awardOnComplete(any());
    }

    // ===== 삭제 =====

    @Test
    @DisplayName("삭제: 소프트 삭제 + DELETE 이력 + 버전 +1")
    void deleteItem_softDeletes() throws Exception {
        ScheduleItem it = item(1004L, IN_PERIOD, 0);
        when(scheduleItemRepository.findWithScheduleById(1004L)).thenReturn(Optional.of(it));

        service.deleteItem(USER_ID, 1004L);

        assertThat(it.getDeletedAt()).isNotNull();
        assertThat(schedule.getCurrentVersion()).isEqualTo(2);
        verify(changeLogger).log(eq(SCHEDULE_ID), eq(1004L), eq(USER_ID), eq(ChangeAction.DELETE),
                eq(ChangeSource.USER), eq(2), any(), any(), eq(null));
    }
}
