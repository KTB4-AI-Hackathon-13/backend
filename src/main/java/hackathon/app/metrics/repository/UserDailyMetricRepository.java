package hackathon.app.metrics.repository;

import hackathon.app.metrics.entity.UserDailyMetric;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserDailyMetricRepository extends JpaRepository<UserDailyMetric, Long> {

    @Query("""
            SELECT m FROM UserDailyMetric m
            WHERE m.userId = :userId
              AND ((:categoryId IS NULL AND m.categoryId IS NULL) OR m.categoryId = :categoryId)
              AND m.metricDate BETWEEN :from AND :to
            ORDER BY m.metricDate ASC
            """)
    List<UserDailyMetric> findRange(@Param("userId") Long userId,
                                    @Param("categoryId") Long categoryId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM UserDailyMetric m
            WHERE m.userId = :userId
              AND ((:categoryId IS NULL AND m.categoryId IS NULL) OR m.categoryId = :categoryId)
              AND m.metricDate BETWEEN :from AND :to
            """)
    int deleteRange(@Param("userId") Long userId,
                    @Param("categoryId") Long categoryId,
                    @Param("from") LocalDate from,
                    @Param("to") LocalDate to);
}
