package hackathon.app.domain.schedule.service;

import hackathon.app.domain.schedule.dto.ScheduleDetailResponse;
import hackathon.app.domain.schedule.dto.ScheduleSummaryResponse;
import hackathon.app.domain.schedule.dto.ScheduleUpdateRequest;
import hackathon.app.domain.schedule.entity.ChangeAction;
import hackathon.app.domain.schedule.entity.ChangeSource;
import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.schedule.entity.ScheduleStatus;
import hackathon.app.domain.schedule.repository.ScheduleRepository;
import hackathon.app.domain.schedule.repository.ScheduleSpecs;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.repository.PuzzleCountProjection;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import hackathon.app.global.common.CursorCodec;
import hackathon.app.global.common.CursorPage;
import hackathon.app.global.error.BusinessException;
import hackathon.app.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final ScheduleChangeLogger changeLogger;
    private final Clock clock;

    /** GET /schedules — id 내림차순 커서 페이징 + 스케줄별 퍼즐 수 */
    public CursorPage<ScheduleSummaryResponse> getSchedules(Long userId, ScheduleStatus status,
                                                            Integer size, String cursor) {
        int pageSize = normalizeSize(size);
        Long cursorId = CursorCodec.decode(cursor);

        Specification<Schedule> spec = Specification.allOf(
                ScheduleSpecs.ownedBy(userId),
                ScheduleSpecs.hasStatus(status),
                ScheduleSpecs.idLessThan(cursorId));

        // size + 1 건을 조회해서 다음 페이지 존재 여부를 판단한다.
        List<Schedule> rows = scheduleRepository.findAll(spec,
                PageRequest.of(0, pageSize + 1, Sort.by(Sort.Direction.DESC, "id"))).getContent();
        boolean hasNext = rows.size() > pageSize;
        List<Schedule> schedules = hasNext ? rows.subList(0, pageSize) : rows;

        Map<Long, PuzzleCountProjection> counts = countPuzzles(schedules);
        List<ScheduleSummaryResponse> items = schedules.stream()
                .map(s -> {
                    PuzzleCountProjection c = counts.get(s.getId());
                    return ScheduleSummaryResponse.of(s,
                            c == null ? 0 : c.getPuzzleCount(),
                            c == null ? 0 : c.getCompletedPuzzleCount());
                })
                .toList();

        String nextCursor = schedules.isEmpty() ? null : CursorCodec.encode(schedules.getLast().getId());
        return CursorPage.of(items, nextCursor, hasNext);
    }

    /** GET /schedules/{scheduleId} — 요약 + 날짜별 작업 + 퍼즐 수 */
    public ScheduleDetailResponse getSchedule(Long userId, Long scheduleId) {
        Schedule schedule = getOwnedSchedule(userId, scheduleId);
        List<ScheduleItem> items = scheduleItemRepository
                .findBySchedule_IdOrderByScheduledDateAscPositionAscPriorityAscIdAsc(scheduleId);
        return ScheduleDetailResponse.of(schedule, items);
    }

    /** PATCH /schedules/{scheduleId} — 제목/설명/기간 수정 + 변경 이력 기록 */
    @Transactional
    public ScheduleSummaryResponse updateSchedule(Long userId, Long scheduleId, ScheduleUpdateRequest request) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "변경할 필드가 최소 하나 필요합니다.");
        }
        Schedule schedule = getOwnedSchedule(userId, scheduleId);

        LocalDate newStart = request.startDate() != null ? request.startDate() : schedule.getStartDate();
        LocalDate newEnd = request.endDate() != null ? request.endDate() : schedule.getEndDate();
        if (newStart.isAfter(newEnd)) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_PERIOD);
        }
        boolean periodChanged = !newStart.equals(schedule.getStartDate()) || !newEnd.equals(schedule.getEndDate());
        if (periodChanged) {
            long outside = scheduleItemRepository.countOutsidePeriod(
                    scheduleId, newStart, newEnd, ScheduleItemStatus.CANCELLED);
            if (outside > 0) {
                throw new BusinessException(ErrorCode.ITEMS_OUTSIDE_SCHEDULE_PERIOD,
                        "변경하려는 기간 밖에 작업이 " + outside + "건 존재합니다.");
            }
        }

        Map<String, Object> before = snapshot(schedule);
        schedule.update(request.title(), request.description(), request.startDate(), request.endDate());
        Map<String, Object> after = snapshot(schedule);

        changeLogger.log(schedule.getId(), null, userId, ChangeAction.UPDATE, ChangeSource.USER,
                schedule.getCurrentVersion(), before, after, null);

        PuzzleCountProjection c = countPuzzles(List.of(schedule)).get(schedule.getId());
        return ScheduleSummaryResponse.of(schedule,
                c == null ? 0 : c.getPuzzleCount(),
                c == null ? 0 : c.getCompletedPuzzleCount());
    }

    /** DELETE /schedules/{scheduleId} — 스케줄 + 하위 작업 소프트 삭제, 변경 이력 기록 */
    @Transactional
    public void deleteSchedule(Long userId, Long scheduleId) {
        Schedule schedule = getOwnedSchedule(userId, scheduleId);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        Map<String, Object> before = snapshot(schedule);
        // 스케줄 먼저 소프트 삭제(더티 체킹) → 하위 작업 일괄 소프트 삭제(벌크 업데이트, 실행 전 flush)
        schedule.softDelete(now);
        int deletedItems = scheduleItemRepository.softDeleteAllBySchedule(scheduleId, now);

        changeLogger.log(schedule.getId(), null, userId, ChangeAction.DELETE, ChangeSource.USER,
                schedule.getCurrentVersion(), before, Map.of("deletedAt", now.toString(), "deletedItemCount", deletedItems),
                null);
    }

    // ===== 내부 헬퍼 =====

    /**
     * 본인 소유의 미삭제 스케줄.
     * - 없거나 삭제됨 → 404 SCHEDULE_NOT_FOUND
     * - 타인 소유      → 403 FORBIDDEN
     */
    public Schedule getOwnedSchedule(Long userId, Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        if (!schedule.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return schedule;
    }

    private Map<Long, PuzzleCountProjection> countPuzzles(List<Schedule> schedules) {
        if (schedules.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = schedules.stream().map(Schedule::getId).toList();
        return scheduleItemRepository
                .countPuzzlesByScheduleIds(ids, ScheduleItemStatus.COMPLETED, ScheduleItemStatus.CANCELLED).stream()
                .collect(java.util.stream.Collectors.toMap(PuzzleCountProjection::getScheduleId, Function.identity()));
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private Map<String, Object> snapshot(Schedule s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", s.getTitle());
        m.put("description", s.getDescription());
        m.put("status", s.getStatus().name());
        m.put("startDate", s.getStartDate().toString());
        m.put("endDate", s.getEndDate().toString());
        m.put("currentVersion", s.getCurrentVersion());
        return m;
    }
}
