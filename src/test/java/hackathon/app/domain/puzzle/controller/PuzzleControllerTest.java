package hackathon.app.domain.puzzle.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.common.error.GlobalExceptionHandler;
import hackathon.app.domain.puzzle.dto.PuzzleDetailResponse;
import hackathon.app.domain.puzzle.dto.PuzzleSummaryResponse;
import hackathon.app.domain.puzzle.entity.PuzzleStatus;
import hackathon.app.domain.puzzle.entity.PuzzleVisibility;
import hackathon.app.domain.puzzle.service.PuzzleService;
import hackathon.app.global.auth.HeaderLoginUserProvider;
import hackathon.app.global.auth.LoginUserArgumentResolver;
import hackathon.app.global.common.CursorPage;
import hackathon.app.global.common.RequestIdFilter;
import hackathon.app.global.config.WebConfig;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PuzzleController.class)
// 슬라이스 테스트에서는 세션 대신 X-User-Id 헤더로 사용자를 식별한다 (HeaderLoginUserProvider 를 빈으로 등록)
@Import({WebConfig.class, LoginUserArgumentResolver.class, HeaderLoginUserProvider.class,
        GlobalExceptionHandler.class, RequestIdFilter.class})
class PuzzleControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PuzzleService puzzleService;

    private PuzzleSummaryResponse summary() {
        return new PuzzleSummaryResponse(7L, 104L, "운동 루틴", PuzzleStatus.IN_PROGRESS, PuzzleVisibility.PUBLIC,
                null, 3, 1, null);
    }

    @Test
    @DisplayName("GET /puzzles/mine — 커서 목록 + 진행률")
    void getMyPuzzles_returnsCursorPage() throws Exception {
        when(puzzleService.getMyPuzzles(eq(1L), isNull(), isNull(), isNull()))
                .thenReturn(CursorPage.of(List.of(summary()), "Nw", true));

        mockMvc.perform(get("/api/v1/puzzles/mine").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(7))
                .andExpect(jsonPath("$.data.items[0].pieceCount").value(3))
                .andExpect(jsonPath("$.data.items[0].earnedPieceCount").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("GET /puzzles/mine — 인증 없으면 401")
    void getMyPuzzles_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/puzzles/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("GET /puzzles/mine — size=0 이면 400")
    void getMyPuzzles_invalidSize_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/puzzles/mine").header("X-User-Id", "1").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
    }

    @Test
    @DisplayName("GET /puzzles/{id} — 조각별 획득 상태")
    void getPuzzle_returnsPieces() throws Exception {
        PuzzleDetailResponse.Piece earned = new PuzzleDetailResponse.Piece(11L, 1301L, "하체 운동",
                LocalDate.of(2026, 8, 17), true, 0, OffsetDateTime.parse("2026-08-19T21:00:00+09:00"));
        PuzzleDetailResponse.Piece notEarned = new PuzzleDetailResponse.Piece(null, 1302L, "상체 운동",
                LocalDate.of(2026, 8, 19), false, null, null);
        when(puzzleService.getPuzzle(1L, 7L)).thenReturn(new PuzzleDetailResponse(7L, 104L, "운동 루틴",
                PuzzleStatus.IN_PROGRESS, PuzzleVisibility.PUBLIC, null, 2, 1, null, List.of(earned, notEarned)));

        mockMvc.perform(get("/api/v1/puzzles/7").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pieces[0].earned").value(true))
                .andExpect(jsonPath("$.data.pieces[0].position").value(0))
                .andExpect(jsonPath("$.data.pieces[1].earned").value(false))
                .andExpect(jsonPath("$.data.pieces[1].pieceId").isEmpty());
    }

    @Test
    @DisplayName("GET /puzzles/{id} — 없으면 404")
    void getPuzzle_notFound_returns404() throws Exception {
        when(puzzleService.getPuzzle(1L, 999L)).thenThrow(new ApiException(ErrorCode.PUZZLE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/puzzles/999").header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PUZZLE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /puzzles/{id} — 타인의 미완성 퍼즐이면 403")
    void getPuzzle_forbidden_returns403() throws Exception {
        when(puzzleService.getPuzzle(1L, 7L)).thenThrow(new ApiException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/v1/puzzles/7").header("X-User-Id", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /users/{userId}/public-puzzles — 인증 없이도 조회된다")
    void getPublicPuzzles_withoutAuth_returns200() throws Exception {
        when(puzzleService.getPublicPuzzles(eq(1L), isNull(), isNull()))
                .thenReturn(CursorPage.of(List.of(summary()), null, false));

        mockMvc.perform(get("/api/v1/users/1/public-puzzles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(7));
    }
}
