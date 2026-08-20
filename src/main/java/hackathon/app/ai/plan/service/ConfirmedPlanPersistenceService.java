package hackathon.app.ai.plan.service;

import hackathon.app.ai.plan.dto.DailyTask;
import hackathon.app.ai.plan.dto.SchedulePlan;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.conversation.ConversationException;
import hackathon.app.conversation.ConversationRepository;
import hackathon.app.conversation.domain.Conversation;
import hackathon.app.domain.puzzle.service.PuzzlePieceAwardService;
import hackathon.app.domain.schedule.entity.ChangeAction;
import hackathon.app.domain.schedule.entity.ChangeSource;
import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.schedule.entity.ScheduleStatus;
import hackathon.app.domain.schedule.repository.ScheduleRepository;
import hackathon.app.domain.schedule.service.ScheduleChangeLogger;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.policy.DailyTaskLimitProvider;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** confirmed=true인 최종 계획만 실제 스케줄과 작업으로 저장한다. */
@Service
@RequiredArgsConstructor
public class ConfirmedPlanPersistenceService {
    private static final long NO_EXCLUDE = -1L;

    private final ConversationRepository conversationRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemRepository itemRepository;
    private final ScheduleChangeLogger changeLogger;
    private final DailyTaskLimitProvider dailyTaskLimitProvider;
    private final PuzzlePieceAwardService puzzlePieceAwardService;
    private final Clock clock;

    @Transactional
    public Long save(Long userId, String conversationId, String goalSummary, SchedulePlan plan) {
        Conversation conversation = conversationRepository.findOwnedForUpdate(conversationId, userId)
                .orElseThrow(ConversationException::notFound);
        if (conversation.getScheduleId() != null) {
            Schedule existing = scheduleRepository.findById(conversation.getScheduleId())
                    .orElseThrow(() -> new ApiException(ErrorCode.SCHEDULE_NOT_FOUND));
            if (!existing.isOwnedBy(userId)) throw new ApiException(ErrorCode.FORBIDDEN);
            return existing.getId();
        }

        List<DailyTask> tasks = validate(plan);
        LocalDate start = tasks.stream().map(DailyTask::scheduled_date).min(LocalDate::compareTo).orElseThrow();
        LocalDate end = tasks.stream().map(DailyTask::scheduled_date).max(LocalDate::compareTo).orElseThrow();
        validateDailyLimits(userId, tasks);

        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .userId(userId)
                .title(goalSummary.length() <= 200 ? goalSummary : goalSummary.substring(0, 200))
                .description(plan.summary())
                .status(ScheduleStatus.ACTIVE)
                .source(ChangeSource.AI)
                .startDate(start)
                .endDate(end)
                .build());

        for (DailyTask task : tasks) {
            ScheduleItem item = ScheduleItem.builder()
                    .schedule(schedule)
                    .categoryId(null)
                    .title(task.title())
                    .description(task.description())
                    .scheduledDate(task.scheduled_date())
                    .workload(null)
                    .estimatedMinutes(task.estimated_min())
                    .priority(3)
                    .position(itemRepository.nextPosition(schedule.getId(), task.scheduled_date()))
                    .source(ChangeSource.AI)
                    .build();
            itemRepository.save(item);
            schedule.increaseVersion();
            changeLogger.log(schedule.getId(), item.getId(), userId, ChangeAction.CREATE, ChangeSource.AI,
                    schedule.getCurrentVersion(), null, snapshot(item), null);
        }

        conversation.linkSchedule(schedule.getId(), LocalDateTime.now(clock));
        puzzlePieceAwardService.refreshOnItemsChanged(schedule);
        return schedule.getId();
    }

    private List<DailyTask> validate(SchedulePlan plan) {
        if (plan == null || plan.summary() == null || plan.summary().isBlank()
                || plan.daily_tasks() == null || plan.daily_tasks().isEmpty()) {
            throw new ApiException(ErrorCode.PLAN_INFORMATION_INCOMPLETE);
        }
        for (DailyTask task : plan.daily_tasks()) {
            if (task == null || task.scheduled_date() == null || task.title() == null || task.title().isBlank()
                    || task.title().length() > 200
                    || task.estimated_min() == null
                    || task.estimated_min() < 1) {
                throw new ApiException(ErrorCode.PLAN_INFORMATION_INCOMPLETE);
            }
        }
        return plan.daily_tasks();
    }

    private void validateDailyLimits(Long userId, List<DailyTask> tasks) {
        int limit = dailyTaskLimitProvider.maxDailyTasks(userId);
        Map<LocalDate, Long> additions = new LinkedHashMap<>();
        tasks.forEach(task -> additions.merge(task.scheduled_date(), 1L, Long::sum));
        additions.forEach((date, count) -> {
            long current = itemRepository.countUserItemsOnDate(
                    userId, date, ScheduleItemStatus.CANCELLED, NO_EXCLUDE);
            if (current + count > limit) {
                throw new ApiException(ErrorCode.MAX_DAILY_TASKS_EXCEEDED,
                        date + "의 작업 수가 하루 최대 " + limit + "개를 초과합니다.");
            }
        });
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
