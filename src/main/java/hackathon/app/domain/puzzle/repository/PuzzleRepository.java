package hackathon.app.domain.puzzle.repository;

import hackathon.app.domain.puzzle.entity.Puzzle;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PuzzleRepository extends JpaRepository<Puzzle, Long>, JpaSpecificationExecutor<Puzzle> {

    /** 스케줄당 퍼즐 1개 (uk_puzzle_schedule) */
    Optional<Puzzle> findByScheduleId(Long scheduleId);
}
