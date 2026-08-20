package hackathon.app.domain.puzzle.controller;

import hackathon.app.auth.application.AuthService;
import hackathon.app.common.api.ApiResponse;
import hackathon.app.domain.puzzle.dto.GalleryPuzzleDetailResponse;
import hackathon.app.domain.puzzle.dto.GalleryPuzzleResponse;
import hackathon.app.domain.puzzle.service.PuzzleService;
import hackathon.app.global.auth.LoginUser;
import hackathon.app.global.auth.LoginUserInfo;
import hackathon.app.global.common.ApiPaths;
import hackathon.app.global.common.CursorPage;
import hackathon.app.user.domain.User;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/gallery")
@RequiredArgsConstructor
@Validated
public class GalleryController {
    private final PuzzleService puzzleService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<CursorPage<GalleryPuzzleResponse>> gallery(
            @CookieValue(name = "SESSION", required = false) String sessionId,
            @RequestParam(required = false, defaultValue = "LATEST") String sort,
            @RequestParam(required = false) @Min(1) @Max(PuzzleService.MAX_PAGE_SIZE) Integer size,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.of(puzzleService.getGallery(optionalUserId(sessionId), sort, size, cursor));
    }

    @GetMapping("/puzzles/{puzzleId}")
    public ApiResponse<GalleryPuzzleDetailResponse> galleryPuzzle(
            @CookieValue(name = "SESSION", required = false) String sessionId,
            @PathVariable Long puzzleId) {
        return ApiResponse.of(puzzleService.getGalleryPuzzle(optionalUserId(sessionId), puzzleId));
    }

    @PutMapping("/puzzles/{puzzleId}/like")
    public ApiResponse<GalleryPuzzleResponse> like(@LoginUser LoginUserInfo loginUser,
                                                   @PathVariable Long puzzleId) {
        return ApiResponse.of(puzzleService.like(loginUser.userId(), puzzleId));
    }

    @DeleteMapping("/puzzles/{puzzleId}/like")
    public ResponseEntity<Void> unlike(@LoginUser LoginUserInfo loginUser, @PathVariable Long puzzleId) {
        puzzleService.unlike(loginUser.userId(), puzzleId);
        return ResponseEntity.noContent().build();
    }

    private Long optionalUserId(String sessionId) {
        return authService.findUser(sessionId).map(User::getId).orElse(null);
    }
}
