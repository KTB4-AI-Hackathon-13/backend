package hackathon.app.ai.plan.service;

import hackathon.app.ai.plan.dto.AiPlanResult;
import hackathon.app.ai.plan.dto.AiPlanTask;
import hackathon.app.domain.schedule.service.ScheduleChangeLogger;
import hackathon.app.domain.schedule.service.ScheduleService;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.domain.puzzle.service.PuzzlePieceAwardService;
import hackathon.app.domain.schedule.dto.ScheduleDetailResponse;
import hackathon.app.domain.schedule.entity.ChangeAction;
import hackathon.app.domain.schedule.entity.ChangeSource;
import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.schedule.entity.ScheduleStatus;
import hackathon.app.domain.schedule.repository.ScheduleRepository;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.policy.CategoryChecker;
import hackathon.app.domain.scheduleitem.policy.DailyTaskLimitProvider;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiPlanPersistenceService {
    private static final long NO_EXCLUDE = -1L;

    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemRepository itemRepository;
    private final ScheduleService scheduleService;
    private final ScheduleChangeLogger changeLogger;
    private final DailyTaskLimitProvider dailyTaskLimitProvider;
    private final CategoryChecker categoryChecker;
    private final PuzzlePieceAwardService puzzlePieceAwardService;

    @Transactional
    public ScheduleDetailResponse create(Long userId, String title, Long categoryId, AiPlanResult result) {
        List<AiPlanTask> tasks = validateResult(result, false);
        if (!categoryChecker.existsActive(categoryId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "존재하지 않거나 사용 중지된 카테고리입니다: " + categoryId);
        }
        LocalDate start = tasks.stream().map(AiPlanTask::scheduled_date).min(LocalDate::compareTo).orElseThrow();
        LocalDate end = tasks.stream().map(AiPlanTask::scheduled_date).max(LocalDate::compareTo).orElseThrow();
        validateGeneratedDailyLimits(userId, tasks);

        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .userId(userId).title(title).description(result.summary())
                .status(ScheduleStatus.ACTIVE).source(ChangeSource.AI)
                .startDate(start).endDate(end).build());

        for (AiPlanTask task : tasks) {
            ScheduleItem item = itemRepository.save(newItem(schedule, categoryId, task));
            schedule.increaseVersion();
            changeLogger.log(schedule.getId(), item.getId(), userId, ChangeAction.CREATE, ChangeSource.AI,
                    schedule.getCurrentVersion(), null, snapshot(item), null);
        }
        puzzlePieceAwardService.refreshOnItemsChanged(schedule);
        return detail(schedule);
    }

    @Transactional
    public ScheduleDetailResponse revise(Long userId, Long scheduleId, AiPlanResult result) {
        Schedule schedule = scheduleService.getOwnedSchedule(userId, scheduleId);
        List<AiPlanTask> tasks = validateResult(result, true);
        schedule.applyAiSummary(result.summary());

        for (AiPlanTask task : tasks) {
            if (!schedule.containsDate(task.scheduled_date())) {
                throw new ApiException(ErrorCode.DATE_OUTSIDE_SCHEDULE_PERIOD);
            }
            if (task.id() == null) {
                validateDailyLimit(userId, task.scheduled_date(), NO_EXCLUDE);
                ScheduleItem item = itemRepository.save(newItem(schedule, null, task));
                schedule.increaseVersion();
                changeLogger.log(scheduleId, item.getId(), userId, ChangeAction.CREATE, ChangeSource.AI,
                        schedule.getCurrentVersion(), null, snapshot(item), null);
                continue;
            }

            ScheduleItem item = itemRepository.findWithScheduleById(task.id())
                    .orElseThrow(() -> new ApiException(ErrorCode.SCHEDULE_ITEM_NOT_FOUND));
            if (!item.getSchedule().getId().equals(scheduleId)) {
                throw new ApiException(ErrorCode.FORBIDDEN);
            }
            if (!task.scheduled_date().equals(item.getScheduledDate())) {
                validateDailyLimit(userId, task.scheduled_date(), item.getId());
            }
            Map<String, Object> before = snapshot(item);
            item.applyAiPlan(task.title(), task.description(), task.scheduled_date(), task.estimated_min());
            schedule.increaseVersion();
            changeLogger.log(scheduleId, item.getId(), userId, ChangeAction.UPDATE, ChangeSource.AI,
                    schedule.getCurrentVersion(), before, snapshot(item), null);
        }
        puzzlePieceAwardService.refreshOnItemsChanged(schedule);
        return detail(schedule);
    }

    private ScheduleItem newItem(Schedule schedule, Long categoryId, AiPlanTask task) {
        ScheduleItem item = ScheduleItem.builder()
                .schedule(schedule).categoryId(categoryId).title(task.title()).description(task.description())
                .scheduledDate(task.scheduled_date()).workload(1).priority(3)
                .position(itemRepository.nextPosition(schedule.getId(), task.scheduled_date()))
                .source(ChangeSource.AI).build();
        item.setEstimatedMinutes(task.estimated_min());
        return item;
    }

    private List<AiPlanTask> validateResult(AiPlanResult result, boolean allowExistingId) {
        if (result == null || result.summary() == null || result.summary().isBlank()
                || result.tasks() == null || result.tasks().isEmpty()) {
            throw new ApiException(ErrorCode.PLAN_INFORMATION_INCOMPLETE);
        }
        for (AiPlanTask task : result.tasks()) {
            if (task == null || task.scheduled_date() == null || task.title() == null || task.title().isBlank()
                    || task.title().length() > 200 || task.estimated_min() == null || task.estimated_min() < 1
                    || (!allowExistingId && task.id() != null)) {
                throw new ApiException(ErrorCode.PLAN_INFORMATION_INCOMPLETE);
            }
        }
        return result.tasks();
    }

    private void validateGeneratedDailyLimits(Long userId, List<AiPlanTask> tasks) {
        int limit = dailyTaskLimitProvider.maxDailyTasks(userId);
        Map<LocalDate, Long> additions = new LinkedHashMap<>();
        for (AiPlanTask task : tasks) additions.merge(task.scheduled_date(), 1L, Long::sum);
        additions.forEach((date, count) -> {
            long current = itemRepository.countUserItemsOnDate(userId, date, ScheduleItemStatus.CANCELLED, NO_EXCLUDE);
            if (current + count > limit) throw new ApiException(ErrorCode.MAX_DAILY_TASKS_EXCEEDED,
                    date + "의 작업 수가 하루 최대 " + limit + "개를 초과합니다.");
        });
    }

    private void validateDailyLimit(Long userId, LocalDate date, long excludeId) {
        int limit = dailyTaskLimitProvider.maxDailyTasks(userId);
        long current = itemRepository.countUserItemsOnDate(userId, date, ScheduleItemStatus.CANCELLED, excludeId);
        if (current >= limit) throw new ApiException(ErrorCode.MAX_DAILY_TASKS_EXCEEDED);
    }

    private ScheduleDetailResponse detail(Schedule schedule) {
        return ScheduleDetailResponse.of(schedule,
                itemRepository.findBySchedule_IdOrderByScheduledDateAscPositionAscPriorityAscIdAsc(schedule.getId()));
    }

    private Map<String, Object> snapshot(ScheduleItem item) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("title", item.getTitle());
        value.put("description", item.getDescription());
        value.put("scheduledDate", item.getScheduledDate());
        value.put("estimatedMinutes", item.getEstimatedMinutes());
        return value;
    }
}
