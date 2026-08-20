package hackathon.app.ai.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hackathon.app.ai.plan.dto.DailyTask;
import hackathon.app.ai.plan.dto.SchedulePlan;
import hackathon.app.category.entity.Category;
import hackathon.app.category.repository.CategoryRepository;
import hackathon.app.conversation.ConversationRepository;
import hackathon.app.conversation.domain.Conversation;
import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.repository.PuzzleRepository;
import hackathon.app.domain.puzzle.service.PuzzlePieceAwardService;
import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.schedule.repository.ScheduleRepository;
import hackathon.app.domain.schedule.service.ScheduleChangeLogger;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.policy.DailyTaskLimitProvider;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ConfirmedPlanPersistenceServiceTest {

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
    private final PuzzleRepository puzzleRepository = mock(PuzzleRepository.class);
    private final ScheduleItemRepository itemRepository = mock(ScheduleItemRepository.class);
    private final ScheduleChangeLogger changeLogger = mock(ScheduleChangeLogger.class);
    private final DailyTaskLimitProvider dailyTaskLimitProvider = mock(DailyTaskLimitProvider.class);
    private final PuzzlePieceAwardService puzzlePieceAwardService = mock(PuzzlePieceAwardService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-20T21:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final ConfirmedPlanPersistenceService service = new ConfirmedPlanPersistenceService(
            conversationRepository,
            categoryRepository,
            scheduleRepository,
            puzzleRepository,
            itemRepository,
            changeLogger,
            dailyTaskLimitProvider,
            puzzlePieceAwardService,
            clock);

    @Test
    void createsPuzzleWithSelectedImageWhenPlanIsConfirmed() {
        Conversation conversation = mock(Conversation.class);
        Category category = mock(Category.class);
        LocalDate date = LocalDate.of(2026, 8, 21);
        SchedulePlan plan = new SchedulePlan("매일 발성 연습", List.of(
                new DailyTask(null, date, "복식 호흡 익히기", null, 30)));
        when(conversationRepository.findOwnedForUpdate("conversation", 5L))
                .thenReturn(Optional.of(conversation));
        when(conversation.getScheduleId()).thenReturn(null);
        when(categoryRepository.findByNameAndActiveTrue("음악")).thenReturn(Optional.of(category));
        when(category.getId()).thenReturn(7L);
        when(dailyTaskLimitProvider.maxDailyTasks(5L)).thenReturn(5);
        when(itemRepository.countUserItemsOnDate(anyLong(), any(), any(), anyLong())).thenReturn(0L);
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> {
            Schedule schedule = invocation.getArgument(0);
            ReflectionTestUtils.setField(schedule, "id", 12L);
            return schedule;
        });
        when(itemRepository.nextPosition(12L, date)).thenReturn(0);
        when(itemRepository.save(any(ScheduleItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(puzzleRepository.findByScheduleId(12L)).thenReturn(Optional.empty());
        when(puzzleRepository.save(any(Puzzle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Long scheduleId = service.save(
                5L, "conversation", "노래 실력 향상", "음악", plan, 77L);

        assertThat(scheduleId).isEqualTo(12L);
        ArgumentCaptor<Puzzle> puzzleCaptor = ArgumentCaptor.forClass(Puzzle.class);
        verify(puzzleRepository).save(puzzleCaptor.capture());
        assertThat(puzzleCaptor.getValue().getScheduleId()).isEqualTo(12L);
        assertThat(puzzleCaptor.getValue().getUserId()).isEqualTo(5L);
        assertThat(puzzleCaptor.getValue().getImageId()).isEqualTo(77L);
        assertThat(puzzleCaptor.getValue().getTitle()).isEqualTo("노래 실력 향상");
    }
}
