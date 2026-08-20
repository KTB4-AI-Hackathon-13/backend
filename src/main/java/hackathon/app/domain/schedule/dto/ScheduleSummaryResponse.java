package hackathon.app.domain.schedule.dto;

import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.schedule.entity.ScheduleStatus;
import java.time.LocalDate;

/** 스케줄 목록 항목 / 수정 응답 — 설계서 "스케줄 응답 필드" 8개. 퍼즐 수는 조회 시 계산 */
public record ScheduleSummaryResponse(
        Long id,
        Long categoryId,
        String title,
        ScheduleStatus status,
        LocalDate startDate,
        LocalDate endDate,
        int currentVersion,
        long puzzleCount,
        long completedPuzzleCount
) {
    /** categoryId 도입 전 내부 호출 호환용. */
    public ScheduleSummaryResponse(Long id, String title, ScheduleStatus status,
                                   LocalDate startDate, LocalDate endDate, int currentVersion,
                                   long puzzleCount, long completedPuzzleCount) {
        this(id, null, title, status, startDate, endDate, currentVersion, puzzleCount, completedPuzzleCount);
    }

    public static ScheduleSummaryResponse of(Schedule schedule, long puzzleCount, long completedPuzzleCount) {
        return new ScheduleSummaryResponse(
                schedule.getId(),
                schedule.getCategoryId(),
                schedule.getTitle(),
                schedule.getStatus(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                schedule.getCurrentVersion(),
                puzzleCount,
                completedPuzzleCount
        );
    }
}
