package hackathon.app.domain.scheduleitem.repository;

/** 스케줄별 퍼즐 수 집계 결과 (저장하지 않고 schedule_items 에서 COUNT 로 계산) */
public interface PuzzleCountProjection {

    Long getScheduleId();

    long getPuzzleCount();

    long getCompletedPuzzleCount();
}
