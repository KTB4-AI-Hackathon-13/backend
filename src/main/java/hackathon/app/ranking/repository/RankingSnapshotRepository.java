package hackathon.app.ranking.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import hackathon.app.ranking.entity.RankingSnapshot;
import hackathon.app.ranking.enums.PeriodType;
import hackathon.app.ranking.enums.RankingScope;
import hackathon.app.ranking.enums.RankingType;

/**
 * categoryId 가 nullable 이라 파생 쿼리(findBy...CategoryId(null))를 쓰면 SQL 이 `= null` 로 번역되어
 * 한 건도 매칭되지 않는다. 그래서 모든 조회를 @Query JPQL 로 직접 쓴다.
 */
public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshot, Long> {

    @Query("""
        select max(s.rankingDate) from RankingSnapshot s
        where s.rankingType = :type
          and s.periodType = :period
          and s.scope = :scope
          and ((:categoryId is null and s.categoryId is null) or s.categoryId = :categoryId)
        """)
    LocalDate findLatestRankingDate(@Param("type") RankingType type,
                                    @Param("period") PeriodType period,
                                    @Param("scope") RankingScope scope,
                                    @Param("categoryId") Long categoryId);

    @Query("""
        select s from RankingSnapshot s
        where s.rankingDate = :date
          and s.rankingType = :type
          and s.periodType = :period
          and s.scope = :scope
          and ((:categoryId is null and s.categoryId is null) or s.categoryId = :categoryId)
        order by s.rankNo asc
        """)
    List<RankingSnapshot> findTopRankings(@Param("date") LocalDate date,
                                          @Param("type") RankingType type,
                                          @Param("period") PeriodType period,
                                          @Param("scope") RankingScope scope,
                                          @Param("categoryId") Long categoryId,
                                          Pageable pageable);

    @Query("""
        select s from RankingSnapshot s
        where s.rankingDate = :date
          and s.rankingType = :type
          and s.periodType = :period
          and s.scope = :scope
          and ((:categoryId is null and s.categoryId is null) or s.categoryId = :categoryId)
          and s.userId = :userId
        """)
    Optional<RankingSnapshot> findByUser(@Param("date") LocalDate date,
                                         @Param("type") RankingType type,
                                         @Param("period") PeriodType period,
                                         @Param("scope") RankingScope scope,
                                         @Param("categoryId") Long categoryId,
                                         @Param("userId") Long userId);

    @Query("""
        select count(s) from RankingSnapshot s
        where s.rankingDate = :date
          and s.rankingType = :type
          and s.periodType = :period
          and s.scope = :scope
          and ((:categoryId is null and s.categoryId is null) or s.categoryId = :categoryId)
        """)
    long countParticipants(@Param("date") LocalDate date,
                           @Param("type") RankingType type,
                           @Param("period") PeriodType period,
                           @Param("scope") RankingScope scope,
                           @Param("categoryId") Long categoryId);

    @Query("select max(s.rankingDate) from RankingSnapshot s")
    LocalDate findLatestSnapshotDate();

    boolean existsByRankingDateAndRankingTypeAndPeriodTypeAndScopeAndCategoryIdIsNull(
            LocalDate rankingDate,
            RankingType rankingType,
            PeriodType periodType,
            RankingScope scope);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    int deleteByRankingDate(LocalDate rankingDate);
}
