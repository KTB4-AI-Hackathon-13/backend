package hackathon.app.ranking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.domain.puzzle.entity.PuzzlePiece;
import hackathon.app.domain.puzzle.entity.PuzzleStatus;
import hackathon.app.domain.puzzle.entity.PuzzleVisibility;
import hackathon.app.domain.puzzle.repository.PuzzlePieceRepository;
import hackathon.app.domain.puzzle.repository.PuzzleRepository;
import hackathon.app.domain.puzzle.repository.PuzzleSpecs;
import hackathon.app.global.config.JpaConfig;
import hackathon.app.user.domain.User;
import hackathon.app.user.infrastructure.JpaUserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(JpaConfig.class)
@ActiveProfiles("test")
class RankingAggregateRepositoryTest {

    @Autowired JpaUserRepository userRepository;
    @Autowired PuzzleRepository puzzleRepository;
    @Autowired PuzzlePieceRepository puzzlePieceRepository;

    @Test
    @DisplayName("랭킹 집계 쿼리는 사용자별 조각 획득 시각과 완성 퍼즐 수를 반환한다")
    void aggregateQueries_returnRankingSources() {
        User user = userRepository.saveAndFlush(
                User.create("ranking@example.com", "hash", "랭킹사용자"));
        LocalDateTime earnedAt = LocalDateTime.of(2026, 8, 20, 9, 30);

        Puzzle puzzle = Puzzle.builder()
                .scheduleId(100L)
                .userId(user.getId())
                .title("MVP 퍼즐")
                .build();
        puzzle.refreshCompletion(1, 1, earnedAt);
        puzzle = puzzleRepository.saveAndFlush(puzzle);

        puzzlePieceRepository.saveAndFlush(PuzzlePiece.builder()
                .puzzleId(puzzle.getId())
                .scheduleItemId(200L)
                .position(0)
                .earnedAt(earnedAt)
                .build());

        Puzzle privatePuzzle = Puzzle.builder()
                .scheduleId(101L)
                .userId(user.getId())
                .title("비공개 퍼즐")
                .visibility(PuzzleVisibility.PRIVATE)
                .build();
        privatePuzzle.refreshCompletion(1, 1, earnedAt);
        puzzleRepository.saveAndFlush(privatePuzzle);

        assertThat(puzzlePieceRepository.findRankingActivities(java.util.List.of(user.getId())))
                .singleElement()
                .satisfies(activity -> {
                    assertThat(activity.getUserId()).isEqualTo(user.getId());
                    assertThat(activity.getScheduleItemId()).isEqualTo(200L);
                    assertThat(activity.getEarnedAt()).isEqualTo(earnedAt);
                });
        assertThat(puzzleRepository.countCompletedByUserIds(
                java.util.List.of(user.getId()), PuzzleStatus.COMPLETED))
                .singleElement()
                .satisfies(count -> {
                    assertThat(count.getUserId()).isEqualTo(user.getId());
                    assertThat(count.getPuzzleCount()).isEqualTo(2);
                });
        assertThat(puzzleRepository.findAll(Specification.allOf(
                PuzzleSpecs.ownedBy(user.getId()),
                PuzzleSpecs.hasStatus(PuzzleStatus.COMPLETED),
                PuzzleSpecs.isPublic())))
                .extracting(Puzzle::getTitle)
                .containsExactly("MVP 퍼즐");
    }
}
