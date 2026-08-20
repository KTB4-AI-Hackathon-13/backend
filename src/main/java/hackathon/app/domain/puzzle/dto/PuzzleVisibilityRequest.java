package hackathon.app.domain.puzzle.dto;

import hackathon.app.domain.puzzle.entity.PuzzleVisibility;
import jakarta.validation.constraints.NotNull;

public record PuzzleVisibilityRequest(@NotNull PuzzleVisibility visibility) {}
