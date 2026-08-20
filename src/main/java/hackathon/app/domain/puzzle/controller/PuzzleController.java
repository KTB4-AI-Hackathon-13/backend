package hackathon.app.domain.puzzle.controller;

import hackathon.app.common.api.ApiResponse;
import hackathon.app.domain.puzzle.dto.PuzzleDetailResponse;
import hackathon.app.domain.puzzle.dto.PuzzleSummaryResponse;
import hackathon.app.domain.puzzle.dto.PuzzleVisibilityRequest;
import hackathon.app.domain.puzzle.entity.PuzzleStatus;
import hackathon.app.domain.puzzle.service.PuzzleService;
import hackathon.app.global.auth.LoginUser;
import hackathon.app.global.auth.LoginUserInfo;
import hackathon.app.global.common.ApiPaths;
import hackathon.app.global.common.CursorPage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 7. 퍼즐 API (MVP) — /api/v1/puzzles, /api/v1/users/{userId}/public-puzzles */
@RestController
@RequestMapping(ApiPaths.V1)
@RequiredArgsConstructor
@Validated
public class PuzzleController {

    private final PuzzleService puzzleService;

    /** 내 퍼즐 목록: status?, size?, cursor? */
    @GetMapping("/puzzles/mine")
    public ApiResponse<CursorPage<PuzzleSummaryResponse>> getMyPuzzles(
            @LoginUser LoginUserInfo loginUser,
            @RequestParam(required = false) PuzzleStatus status,
            @RequestParam(required = false) @Min(1) @Max(PuzzleService.MAX_PAGE_SIZE) Integer size,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.of(puzzleService.getMyPuzzles(loginUser.userId(), status, size, cursor));
    }

    /** 퍼즐 상세 (조각별 획득 상태 포함) */
    @GetMapping("/puzzles/{puzzleId}")
    public ApiResponse<PuzzleDetailResponse> getPuzzle(@LoginUser LoginUserInfo loginUser,
                                                       @PathVariable Long puzzleId) {
        return ApiResponse.of(puzzleService.getPuzzle(loginUser.userId(), puzzleId));
    }

    /** 특정 사용자의 공개 완성 퍼즐 */
    @GetMapping("/users/{userId}/public-puzzles")
    public ApiResponse<CursorPage<PuzzleSummaryResponse>> getPublicPuzzles(
            @PathVariable Long userId,
            @RequestParam(required = false, defaultValue = "LATEST") String sort,
            @RequestParam(required = false) @Min(1) @Max(PuzzleService.MAX_PAGE_SIZE) Integer size,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.of(puzzleService.getPublicPuzzles(userId, sort, size, cursor));
    }

    @PatchMapping("/puzzles/{puzzleId}/visibility")
    public ApiResponse<PuzzleSummaryResponse> changeVisibility(@LoginUser LoginUserInfo loginUser,
            @PathVariable Long puzzleId, @Valid @RequestBody PuzzleVisibilityRequest request) {
        return ApiResponse.of(puzzleService.changeVisibility(loginUser.userId(), puzzleId, request.visibility()));
    }

}
