package hackathon.app.domain.scheduleitem.repository;

import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

    /**
     * 스케줄별 전체/완료 퍼즐 수.
     * 유효한 작업 = deleted_at IS NULL (엔티티 @SQLRestriction 으로 자동 적용) AND status != CANCELLED,
     * 완료 = status = COMPLETED
     */
    @Query("""
            SELECT i.schedule.id AS scheduleId,
                   COUNT(i) AS puzzleCount,
                   SUM(CASE WHEN i.status = :completed THEN 1 ELSE 0 END) AS completedPuzzleCount
            FROM ScheduleItem i
            WHERE i.schedule.id IN :scheduleIds
              AND i.status <> :cancelled
            GROUP BY i.schedule.id
            """)
    List<PuzzleCountProjection> countPuzzlesByScheduleIds(@Param("scheduleIds") Collection<Long> scheduleIds,
                                                         @Param("completed") ScheduleItemStatus completed,
                                                         @Param("cancelled") ScheduleItemStatus cancelled);

    /** 스케줄 상세: 날짜 → position → 우선순위 → id 순 */
    List<ScheduleItem> findBySchedule_IdOrderByScheduledDateAscPositionAscPriorityAscIdAsc(Long scheduleId);

    /** 월별 캘린더 / 오늘 할 일: 사용자의 기간 내 작업 (스케줄 함께 로딩) */
    @Query("""
            SELECT i FROM ScheduleItem i
            LEFT JOIN FETCH i.schedule s
            WHERE i.userId = :userId
              AND (s IS NULL OR s.deletedAt IS NULL)
              AND i.scheduledDate BETWEEN :from AND :to
            ORDER BY i.scheduledDate ASC, i.position ASC, i.priority ASC, i.id ASC
            """)
    List<ScheduleItem> findByUserIdAndDateBetween(@Param("userId") Long userId,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    /** 기간 변경 시 새 기간 밖에 있는 (취소되지 않은) 작업 수 */
    @Query("""
            SELECT COUNT(i) FROM ScheduleItem i
            WHERE i.schedule.id = :scheduleId
              AND i.status <> :cancelled
              AND (i.scheduledDate < :startDate OR i.scheduledDate > :endDate)
            """)
    long countOutsidePeriod(@Param("scheduleId") Long scheduleId,
                            @Param("startDate") LocalDate startDate,
                            @Param("endDate") LocalDate endDate,
                            @Param("cancelled") ScheduleItemStatus cancelled);

    /** 작업 단건 + 소속 스케줄(미삭제) 함께 로딩. 소유자 검사는 서비스에서 */
    @Query("""
            SELECT i FROM ScheduleItem i
            LEFT JOIN FETCH i.schedule s
            WHERE i.id = :itemId
              AND (s IS NULL OR s.deletedAt IS NULL)
            """)
    Optional<ScheduleItem> findWithScheduleById(@Param("itemId") Long itemId);

    /**
     * 하루 최대 작업 수 검사용: 사용자의 모든 스케줄을 합산해 그 날짜의 유효한(CANCELLED 제외) 작업 수.
     * excludeItemId 는 날짜를 옮기는 작업 자신을 제외할 때 사용 (없으면 -1).
     */
    @Query("""
            SELECT COUNT(i) FROM ScheduleItem i
            WHERE i.userId = :userId
              AND i.scheduledDate = :date
              AND i.status <> :cancelled
              AND i.id <> :excludeItemId
            """)
    long countUserItemsOnDate(@Param("userId") Long userId,
                              @Param("date") LocalDate date,
                              @Param("cancelled") ScheduleItemStatus cancelled,
                              @Param("excludeItemId") Long excludeItemId);

    /** 같은 스케줄·같은 날짜의 다음 position (= 현재 최대 + 1, 없으면 0) */
    @Query("""
            SELECT COALESCE(MAX(i.position), -1) + 1 FROM ScheduleItem i
            WHERE i.schedule.id = :scheduleId
              AND i.scheduledDate = :date
            """)
    int nextPosition(@Param("scheduleId") Long scheduleId, @Param("date") LocalDate date);

    /** 단독 작업끼리 같은 날짜에서 사용할 다음 표시 순서 */
    @Query("""
            SELECT COALESCE(MAX(i.position), -1) + 1 FROM ScheduleItem i
            WHERE i.schedule IS NULL
              AND i.userId = :userId
              AND i.scheduledDate = :date
            """)
    int nextStandalonePosition(@Param("userId") Long userId, @Param("date") LocalDate date);

    /** 스케줄 삭제 시 하위 작업 일괄 소프트 삭제 */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE ScheduleItem i SET i.deletedAt = :now WHERE i.schedule.id = :scheduleId AND i.deletedAt IS NULL")
    int softDeleteAllBySchedule(@Param("scheduleId") Long scheduleId, @Param("now") LocalDateTime now);
}
