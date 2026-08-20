package hackathon.app.ai.plan.service;

import hackathon.app.ai.plan.dto.DailyTask;
import hackathon.app.ai.plan.dto.SchedulePlan;
import hackathon.app.category.CategoryType;
import hackathon.app.category.repository.CategoryRepository;
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
import hackathon.app.domain.scheduleitem.entity.ScheduleItemType;
import hackathon.app.domain.scheduleitem.policy.DailyTaskLimitProvider;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** confirmed=true인 최종 계획만 실제 스케줄과 작업으로 저장한다. */
@Service
@RequiredArgsConstructor
public class ConfirmedPlanPersistenceService {
    private static final long NO_EXCLUDE = -1L;

    private final ConversationRepository conversationRepository;
    private final CategoryRepository categoryRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemRepository itemRepository;
    private final ScheduleChangeLogger changeLogger;
    private final DailyTaskLimitProvider dailyTaskLimitProvider;
    private final PuzzlePieceAwardService puzzlePieceAwardService;
    private final Clock clock;

    @Transactional
    public Long save(Long userId, String conversationId, String goalSummary, String category, SchedulePlan plan) {
        Conversation conversation = conversationRepository.findOwnedForUpdate(conversationId, userId)
                .orElseThrow(ConversationException::notFound);
        Long categoryId = resolveCategoryId(category);
        if (conversation.getScheduleId() != null) {
            Schedule existing = scheduleRepository.findById(conversation.getScheduleId())
                    .orElseThrow(() -> new ApiException(ErrorCode.SCHEDULE_NOT_FOUND));
            if (!existing.isOwnedBy(userId)) throw new ApiException(ErrorCode.FORBIDDEN);
            return updateExisting(userId, existing, goalSummary, categoryId, plan);
        }

        List<DailyTask> tasks = validate(plan);
        LocalDate start = tasks.stream().map(DailyTask::scheduled_date).min(LocalDate::compareTo).orElseThrow();
        LocalDate end = tasks.stream().map(DailyTask::scheduled_date).max(LocalDate::compareTo).orElseThrow();
        validateDailyLimits(userId, tasks);

        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .userId(userId)
                .categoryId(categoryId)
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
                    .title(task.title())
                    .description(task.description())
                    .scheduledDate(task.scheduled_date())
                    .estimatedMinutes(task.estimated_min())
                    .itemType(ScheduleItemType.ETC)
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

    private Long updateExisting(Long userId, Schedule schedule, String goalSummary,
                                Long categoryId, SchedulePlan plan) {
        List<DailyTask> tasks = validate(plan);
        List<ScheduleItem> existingItems = itemRepository
                .findBySchedule_IdOrderByScheduledDateAscPositionAscPriorityAscIdAsc(schedule.getId());
        Map<Long, ScheduleItem> itemsById = new HashMap<>();
        existingItems.forEach(item -> itemsById.put(item.getId(), item));

        Set<Long> retainedIds = new HashSet<>();
        Map<LocalDate, Long> proposedCounts = new LinkedHashMap<>();
        existingItems.stream()
                .filter(ScheduleItem::isCompleted)
                .forEach(item -> proposedCounts.merge(item.getScheduledDate(), 1L, Long::sum));

        for (DailyTask task : tasks) {
            if (task.id() == null || task.id().isBlank()) {
                proposedCounts.merge(task.scheduled_date(), 1L, Long::sum);
                continue;
            }
            Long itemId = parseItemId(task.id());
            ScheduleItem item = itemsById.get(itemId);
            if (item == null) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "현재 계획에 속하지 않은 작업 ID입니다: " + task.id());
            }
            if (item.isCompleted()) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "완료한 작업은 AI로 수정할 수 없습니다: " + task.id());
            }
            if (!retainedIds.add(itemId)) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "중복된 작업 ID입니다: " + task.id());
            }
            proposedCounts.merge(task.scheduled_date(), 1L, Long::sum);
        }
        validateExistingDailyLimits(userId, schedule.getId(), proposedCounts);

        LocalDateTime now = LocalDateTime.now(clock);
        for (DailyTask task : tasks) {
            if (task.id() == null || task.id().isBlank()) {
                ScheduleItem created = itemRepository.save(ScheduleItem.builder()
                        .schedule(schedule)
                        .title(task.title())
                        .description(task.description())
                        .scheduledDate(task.scheduled_date())
                        .estimatedMinutes(task.estimated_min())
                        .itemType(ScheduleItemType.ETC)
                        .priority(3)
                        .position(itemRepository.nextPosition(schedule.getId(), task.scheduled_date()))
                        .source(ChangeSource.AI)
                        .build());
                schedule.increaseVersion();
                changeLogger.log(schedule.getId(), created.getId(), userId, ChangeAction.CREATE,
                        ChangeSource.AI, schedule.getCurrentVersion(), null, snapshot(created),
                        "AI 계획 수정으로 작업 생성");
                continue;
            }

            ScheduleItem item = itemsById.get(parseItemId(task.id()));
            Map<String, Object> before = snapshot(item);
            boolean rescheduled = !item.getScheduledDate().equals(task.scheduled_date());
            item.applyAiPlan(task.title(), task.description(), task.scheduled_date(), task.estimated_min());
            schedule.increaseVersion();
            changeLogger.log(schedule.getId(), item.getId(), userId,
                    rescheduled ? ChangeAction.RESCHEDULE : ChangeAction.UPDATE,
                    ChangeSource.AI, schedule.getCurrentVersion(), before, snapshot(item),
                    "AI 계획 수정 반영");
        }

        for (ScheduleItem item : existingItems) {
            if (item.isCompleted() || item.getStatus() == ScheduleItemStatus.CANCELLED
                    || retainedIds.contains(item.getId())) {
                continue;
            }
            Map<String, Object> before = snapshot(item);
            item.softDelete(now);
            schedule.increaseVersion();
            changeLogger.log(schedule.getId(), item.getId(), userId, ChangeAction.DELETE,
                    ChangeSource.AI, schedule.getCurrentVersion(), before, snapshot(item),
                    "AI 수정 계획에서 제외됨");
        }

        List<ScheduleItem> activeItems = itemRepository
                .findBySchedule_IdOrderByScheduledDateAscPositionAscPriorityAscIdAsc(schedule.getId())
                .stream()
                .filter(item -> item.getStatus() != ScheduleItemStatus.CANCELLED)
                .toList();
        if (!activeItems.isEmpty()) {
            LocalDate start = activeItems.stream().map(ScheduleItem::getScheduledDate)
                    .min(LocalDate::compareTo).orElseThrow();
            LocalDate end = activeItems.stream().map(ScheduleItem::getScheduledDate)
                    .max(LocalDate::compareTo).orElseThrow();
            schedule.update(truncate(goalSummary), plan.summary(), start, end, categoryId);
        }
        puzzlePieceAwardService.refreshOnItemsChanged(schedule);
        return schedule.getId();
    }

    private Long parseItemId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "올바르지 않은 작업 ID입니다: " + value);
        }
    }

    private Long resolveCategoryId(String value) {
        CategoryType category = CategoryType.fromDisplayName(value)
                .orElseThrow(() -> new ApiException(ErrorCode.PLAN_INFORMATION_INCOMPLETE,
                        "지원하지 않는 AI 카테고리입니다: " + value));
        return categoryRepository.findByNameAndActiveTrue(category.displayName())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST,
                        "활성 카테고리를 찾을 수 없습니다: " + category.code()))
                .getId();
    }

    private void validateExistingDailyLimits(Long userId, Long scheduleId,
            Map<LocalDate, Long> proposedCounts) {
        int limit = dailyTaskLimitProvider.maxDailyTasks(userId);
        proposedCounts.forEach((date, count) -> {
            long outside = itemRepository.countUserItemsOnDateExcludingSchedule(
                    userId, date, ScheduleItemStatus.CANCELLED, scheduleId);
            if (outside + count > limit) {
                throw new ApiException(ErrorCode.MAX_DAILY_TASKS_EXCEEDED,
                        date + "의 작업 수가 하루 최대 " + limit + "개를 초과합니다.");
            }
        });
    }

    private String truncate(String value) {
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    private List<DailyTask> validate(SchedulePlan plan) {
        if (plan == null || plan.summary() == null || plan.summary().isBlank()
                || plan.daily_tasks() == null || plan.daily_tasks().isEmpty()) {
            throw new ApiException(ErrorCode.PLAN_INFORMATION_INCOMPLETE);
        }
        for (DailyTask task : plan.daily_tasks()) {
            if (task == null || task.scheduled_date() == null || task.title() == null || task.title().isBlank()
                    || task.title().length() > 100
                    || (task.description() != null && task.description().length() > 1000)
                    || task.estimated_min() == null
                    || task.estimated_min() < 1
                    || task.estimated_min() > 1440) {
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
        value.put("itemType", item.getItemType());
        value.put("priority", item.getPriority());
        value.put("status", item.getStatus());
        return value;
    }
}
