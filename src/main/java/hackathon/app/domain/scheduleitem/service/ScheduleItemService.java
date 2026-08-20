package hackathon.app.domain.scheduleitem.service;

import hackathon.app.domain.schedule.entity.ChangeAction;
import hackathon.app.domain.schedule.entity.ChangeSource;
import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.schedule.service.ScheduleChangeLogger;
import hackathon.app.domain.schedule.service.ScheduleService;
import hackathon.app.domain.scheduleitem.dto.ScheduleItemCreateRequest;
import hackathon.app.domain.scheduleitem.dto.ScheduleItemResponse;
import hackathon.app.domain.scheduleitem.dto.ScheduleItemStatusResponse;
import hackathon.app.domain.scheduleitem.dto.ScheduleItemUpdateRequest;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemType;
import hackathon.app.domain.scheduleitem.policy.DailyTaskLimitProvider;
import hackathon.app.domain.scheduleitem.policy.PuzzlePieceAwarder;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 6. 작업 API.
 * 규칙 요약
 * - 작업 날짜는 스케줄 기간 안이어야 한다 → 422 DATE_OUTSIDE_SCHEDULE_PERIOD
 * - 한 날짜의 작업 수(사용자 전체 스케줄 합산, CANCELLED 제외)는 user_preferences.max_daily_tasks(기본 5) 이하 → 422 MAX_DAILY_TASKS_EXCEEDED
 * - 추가/수정/삭제는 schedule_change_logs 에 기록하고 스케줄 버전을 1 올린다 (상태 변경은 기록하지 않음)
 * - COMPLETED 로 바뀌면 PuzzlePieceAwarder 에 조각 지급을 위임한다 (중복 지급 방지는 구현체 책임)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleItemService {

    private static final long NO_EXCLUDE = -1L;

    private final ScheduleItemRepository scheduleItemRepository;
    private final ScheduleService scheduleService;
    private final ScheduleChangeLogger changeLogger;
    private final DailyTaskLimitProvider dailyTaskLimitProvider;
    private final PuzzlePieceAwarder puzzlePieceAwarder;
    private final Clock clock;

    /** POST /schedule-items — 스케줄에 소속되지 않은 단독 작업 생성 */
    @Transactional
    public ScheduleItemResponse createStandaloneItem(Long userId, ScheduleItemCreateRequest request) {
        validateDailyLimit(userId, request.scheduledDate(), NO_EXCLUDE);
        validateEstimatedMinutes(request.estimatedMinutes());
        ScheduleItemType itemType = ScheduleItemType.from(request.itemType());
        int position = request.position() != null
                ? request.position()
                : scheduleItemRepository.nextStandalonePosition(userId, request.scheduledDate());
        ScheduleItem item = scheduleItemRepository.save(ScheduleItem.builder()
                .schedule(null)
                .userId(userId)
                .title(request.title())
                .description(request.description())
                .scheduledDate(request.scheduledDate())
                .estimatedMinutes(request.estimatedMinutes())
                .itemType(itemType)
                .priority(3)
                .position(position)
                .source(ChangeSource.USER)
                .build());
        changeLogger.log(null, item.getId(), userId, ChangeAction.CREATE, ChangeSource.USER,
                1, null, snapshot(item), null);
        return ScheduleItemResponse.from(item);
    }

    /** POST /schedules/{scheduleId}/items */
    @Transactional
    public ScheduleItemResponse createItem(Long userId, Long scheduleId, ScheduleItemCreateRequest request) {
        Schedule schedule = scheduleService.getOwnedSchedule(userId, scheduleId);

        validateDateInPeriod(schedule, request.scheduledDate());
        validateDailyLimit(userId, request.scheduledDate(), NO_EXCLUDE);
        validateEstimatedMinutes(request.estimatedMinutes());
        ScheduleItemType itemType = ScheduleItemType.from(request.itemType());

        int position = request.position() != null
                ? request.position()
                : scheduleItemRepository.nextPosition(scheduleId, request.scheduledDate());

        ScheduleItem item = scheduleItemRepository.save(ScheduleItem.builder()
                .schedule(schedule)
                .userId(userId)
                .title(request.title())
                .description(request.description())
                .scheduledDate(request.scheduledDate())
                .estimatedMinutes(request.estimatedMinutes())
                .itemType(itemType)
                .priority(3)
                .position(position)
                .source(ChangeSource.USER)
                .build());

        schedule.increaseVersion();
        changeLogger.log(schedule.getId(), item.getId(), userId, ChangeAction.CREATE, ChangeSource.USER,
                schedule.getCurrentVersion(), null, snapshot(item), null);
        puzzlePieceAwarder.refreshOnItemsChanged(schedule);   // 조각 수가 늘어 퍼즐이 미완성으로 돌아갈 수 있다

        return ScheduleItemResponse.from(item);
    }

    /** PATCH /schedule-items/{itemId} */
    @Transactional
    public ScheduleItemResponse updateItem(Long userId, Long itemId, ScheduleItemUpdateRequest request) {
        if (request.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "변경할 필드가 최소 하나 필요합니다.");
        }
        ScheduleItem item = getOwnedItem(userId, itemId);
        Schedule schedule = item.getSchedule();

        if (request.scheduledDate() != null && !request.scheduledDate().equals(item.getScheduledDate())) {
            if (schedule != null) validateDateInPeriod(schedule, request.scheduledDate());
            validateDailyLimit(userId, request.scheduledDate(), item.getId());
        }
        if (request.estimatedMinutes() != null) validateEstimatedMinutes(request.estimatedMinutes());
        ScheduleItemType itemType = request.itemType() == null ? null : ScheduleItemType.from(request.itemType());

        Map<String, Object> before = snapshot(item);
        item.update(request.title(), request.description(), request.scheduledDate(),
                request.estimatedMinutes(), itemType, null, request.position());
        Map<String, Object> after = snapshot(item);

        if (schedule != null) schedule.increaseVersion();
        changeLogger.log(schedule == null ? null : schedule.getId(), item.getId(), userId,
                ChangeAction.UPDATE, ChangeSource.USER,
                schedule == null ? 1 : schedule.getCurrentVersion(), before, after, null);

        return ScheduleItemResponse.from(item);
    }

    /** PATCH /schedule-items/{itemId}/status */
    @Transactional
    public ScheduleItemStatusResponse changeStatus(Long userId, Long itemId, ScheduleItemStatus newStatus) {
        ScheduleItem item = getOwnedItem(userId, itemId);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        item.changeStatus(newStatus, now);

        PuzzlePieceAwarder.AwardResult award;
        if (newStatus == ScheduleItemStatus.COMPLETED && item.getSchedule() != null) {
            award = puzzlePieceAwarder.awardOnComplete(item);
        } else {
            award = PuzzlePieceAwarder.AwardResult.none();
            // CANCELLED 로 바뀌면 유효한 작업 수가 줄어 퍼즐이 완성될 수 있다
            if (item.getSchedule() != null) puzzlePieceAwarder.refreshOnItemsChanged(item.getSchedule());
        }

        return ScheduleItemStatusResponse.of(item, award);
    }

    /** DELETE /schedule-items/{itemId} */
    @Transactional
    public void deleteItem(Long userId, Long itemId) {
        ScheduleItem item = getOwnedItem(userId, itemId);
        Schedule schedule = item.getSchedule();
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        Map<String, Object> before = snapshot(item);
        item.softDelete(now);

        if (schedule != null) schedule.increaseVersion();
        changeLogger.log(schedule == null ? null : schedule.getId(), item.getId(), userId,
                ChangeAction.DELETE, ChangeSource.USER,
                schedule == null ? 1 : schedule.getCurrentVersion(), before,
                Map.of("deletedAt", now.toString()), null);
        if (schedule != null) puzzlePieceAwarder.refreshOnItemsChanged(schedule);
    }

    // ===== 내부 헬퍼 =====

    /**
     * 본인 소유 스케줄의 미삭제 작업.
     * - 작업 없음/삭제됨/스케줄 삭제됨 → 404 SCHEDULE_ITEM_NOT_FOUND
     * - 타인 소유 → 403 FORBIDDEN
     */
    public ScheduleItem getOwnedItem(Long userId, Long itemId) {
        ScheduleItem item = scheduleItemRepository.findWithScheduleById(itemId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCHEDULE_ITEM_NOT_FOUND));
        if (!item.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return item;
    }

    private void validateDateInPeriod(Schedule schedule, LocalDate date) {
        if (!schedule.containsDate(date)) {
            throw new ApiException(ErrorCode.DATE_OUTSIDE_SCHEDULE_PERIOD,
                    "작업 날짜 " + date + " 가 스케줄 기간(" + schedule.getStartDate() + " ~ "
                            + schedule.getEndDate() + ") 밖입니다.");
        }
    }

    private void validateDailyLimit(Long userId, LocalDate date, long excludeItemId) {
        int limit = dailyTaskLimitProvider.maxDailyTasks(userId);
        long current = scheduleItemRepository.countUserItemsOnDate(userId, date, ScheduleItemStatus.CANCELLED,
                excludeItemId);
        if (current >= limit) {
            throw new ApiException(ErrorCode.MAX_DAILY_TASKS_EXCEEDED,
                    date + " 에는 이미 " + current + "개의 작업이 있습니다. (하루 최대 " + limit + "개)");
        }
    }

    private void validateEstimatedMinutes(Integer estimatedMinutes) {
        if (estimatedMinutes == null || estimatedMinutes < 1 || estimatedMinutes > 1440) {
            throw new ApiException(ErrorCode.INVALID_ESTIMATED_MINUTES);
        }
    }

    private Map<String, Object> snapshot(ScheduleItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", i.getTitle());
        m.put("description", i.getDescription());
        m.put("scheduledDate", i.getScheduledDate().toString());
        m.put("estimatedMinutes", i.getEstimatedMinutes());
        m.put("itemType", i.getItemType().name());
        m.put("priority", i.getPriority());
        m.put("position", i.getPosition());
        m.put("status", i.getStatus().name());
        return m;
    }
}
