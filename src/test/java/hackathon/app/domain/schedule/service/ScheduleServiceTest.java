package hackathon.app.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hackathon.app.domain.schedule.dto.ScheduleSummaryResponse;
import hackathon.app.domain.schedule.dto.ScheduleUpdateRequest;
import hackathon.app.domain.schedule.entity.ChangeAction;
import hackathon.app.domain.schedule.entity.ChangeSource;
import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.schedule.entity.ScheduleStatus;
import hackathon.app.domain.schedule.repository.ScheduleRepository;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
class ScheduleServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SCHEDULE_ID = 101L;

    @Mock ScheduleRepository scheduleRepository;
    @Mock ScheduleItemRepository scheduleItemRepository;
    @Mock ScheduleChangeLogger changeLogger;
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-08-19T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @InjectMocks ScheduleService scheduleService;

    private Schedule schedule;

    @BeforeEach
    void setUp() {
        schedule = Schedule.builder()
                .userId(USER_ID)
                .title("8월 알고리즘 공부")
                .description("매일 풀기")
                .status(ScheduleStatus.ACTIVE)
                .source(ChangeSource.AI)
                .startDate(LocalDate.of(2026, 8, 17))
                .endDate(LocalDate.of(2026, 8, 30))
                .build();
    }

    @Test
    @DisplayName("상세 조회: 없거나 삭제된 스케줄이면 404 SCHEDULE_NOT_FOUND")
    void getSchedule_notFound_throwsNotFound() {
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.getSchedule(USER_ID, SCHEDULE_ID))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    @DisplayName("상세 조회: 타인 소유 스케줄이면 403 FORBIDDEN")
    void getSchedule_notOwned_throwsForbidden() {
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> scheduleService.getSchedule(999L, SCHEDULE_ID))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("수정: 변경 필드가 하나도 없으면 INVALID_REQUEST")
    void updateSchedule_emptyRequest_throws() {
        ScheduleUpdateRequest request = new ScheduleUpdateRequest(null, null, null, null);

        assertThatThrownBy(() -> scheduleService.updateSchedule(USER_ID, SCHEDULE_ID, request))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(scheduleRepository, never()).findById(any());
    }

    @Test
    @DisplayName("수정: 시작일이 종료일보다 늦으면 INVALID_SCHEDULE_PERIOD(422)")
    void updateSchedule_startAfterEnd_throws() {
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        ScheduleUpdateRequest request = new ScheduleUpdateRequest(null, null, LocalDate.of(2026, 9, 1), null);

        assertThatThrownBy(() -> scheduleService.updateSchedule(USER_ID, SCHEDULE_ID, request))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_SCHEDULE_PERIOD);
    }

    @Test
    @DisplayName("수정: 새 기간 밖에 작업이 있으면 ITEMS_OUTSIDE_SCHEDULE_PERIOD(409), 버전 유지")
    void updateSchedule_itemsOutsidePeriod_throwsConflict() {
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(scheduleItemRepository.countOutsidePeriod(any(), eq(LocalDate.of(2026, 8, 17)),
                eq(LocalDate.of(2026, 8, 20)), eq(ScheduleItemStatus.CANCELLED))).thenReturn(2L);
        ScheduleUpdateRequest request = new ScheduleUpdateRequest(null, null, null, LocalDate.of(2026, 8, 20));

        assertThatThrownBy(() -> scheduleService.updateSchedule(USER_ID, SCHEDULE_ID, request))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.ITEMS_OUTSIDE_SCHEDULE_PERIOD);
        assertThat(schedule.getCurrentVersion()).isEqualTo(1);
        verify(changeLogger, never()).log(any(), any(), any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("수정: 제목만 변경 → 버전 +1, 기간 검증 쿼리 없이 변경 이력 UPDATE 기록")
    void updateSchedule_titleOnly_incrementsVersionAndLogs() {
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(scheduleItemRepository.countPuzzlesByScheduleIds(anyList(), eq(ScheduleItemStatus.COMPLETED),
                eq(ScheduleItemStatus.CANCELLED))).thenReturn(List.of());
        ScheduleUpdateRequest request = new ScheduleUpdateRequest("새 제목", null, null, null);

        ScheduleSummaryResponse response = scheduleService.updateSchedule(USER_ID, SCHEDULE_ID, request);

        assertThat(response.title()).isEqualTo("새 제목");
        assertThat(response.currentVersion()).isEqualTo(2);
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 8, 17));
        verify(scheduleItemRepository, never()).countOutsidePeriod(any(), any(), any(), any());
        verify(changeLogger).log(eq(schedule.getId()), eq(null), eq(USER_ID), eq(ChangeAction.UPDATE),
                eq(ChangeSource.USER), eq(2), any(), any(), eq(null));
    }

    @Test
    @DisplayName("삭제: 스케줄 소프트 삭제 + 하위 작업 일괄 소프트 삭제 + DELETE 이력")
    void deleteSchedule_softDeletesScheduleAndItems() {
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(scheduleItemRepository.softDeleteAllBySchedule(eq(SCHEDULE_ID), any())).thenReturn(3);

        scheduleService.deleteSchedule(USER_ID, SCHEDULE_ID);

        assertThat(schedule.getDeletedAt()).isNotNull();
        verify(scheduleItemRepository).softDeleteAllBySchedule(eq(SCHEDULE_ID), any());
        verify(changeLogger).log(any(), eq(null), eq(USER_ID), eq(ChangeAction.DELETE),
                eq(ChangeSource.USER), eq(1), any(), any(), eq(null));
    }
}
