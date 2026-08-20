package hackathon.app.ranking.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import hackathon.app.common.api.ApiResponse;
import hackathon.app.common.error.GlobalExceptionHandler.ErrorResponse;
import hackathon.app.common.error.GlobalExceptionHandler.FieldError;
import hackathon.app.ranking.dto.response.GetRankingResponse;
import hackathon.app.ranking.service.RankingService;

@RestController
@RequestMapping("/api/v1/rankings")
public class RankingController {

    private static final String INVALID_REQUEST_CODE = "INVALID_REQUEST";
    private static final String INVALID_REQUEST_MESSAGE = "요청값을 확인해주세요.";
    private static final String REQUIRED_PARAMETER_MESSAGE = "필수 파라미터입니다.";
    private static final String NOT_A_NUMBER_MESSAGE = "숫자여야 합니다: ";
    private static final String FIELD_SEPARATOR = ": ";
    private static final String FALLBACK_FIELD = "request";

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping
    ApiResponse<GetRankingResponse> getRankings(
            @CookieValue(name = "SESSION", required = false) String sessionId,
            @RequestParam String type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String period) {
        return ApiResponse.of(rankingService.getRankings(sessionId, type, categoryId, period));
    }

    // 컨트롤러 안에 둔 핸들러라 이 컨트롤러의 예외만 잡는다.
    // 전역 @RestControllerAdvice 로 만들면 다른 사람의 API 응답까지 바뀐다.
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleInvalidParameter(IllegalArgumentException exception) {
        String raw = exception.getMessage();
        int separator = (raw == null) ? -1 : raw.indexOf(FIELD_SEPARATOR);
        if (separator < 0) {
            return badRequest(FALLBACK_FIELD, raw);
        }
        return badRequest(raw.substring(0, separator), raw.substring(separator + FIELD_SEPARATOR.length()));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> handleBadParameter(Exception exception) {
        return switch (exception) {
            case MissingServletRequestParameterException missing ->
                    badRequest(missing.getParameterName(), REQUIRED_PARAMETER_MESSAGE);
            case MethodArgumentTypeMismatchException mismatch ->
                    badRequest(mismatch.getName(), NOT_A_NUMBER_MESSAGE + mismatch.getValue());
            default -> badRequest(FALLBACK_FIELD, exception.getMessage());
        };
    }

    private ResponseEntity<ErrorResponse> badRequest(String field, String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                INVALID_REQUEST_CODE,
                INVALID_REQUEST_MESSAGE,
                List.of(new FieldError(field, message)),
                UUID.randomUUID().toString()));
    }
}
