package hackathon.app.common.error;

import hackathon.app.global.common.RequestIdHolder;
import jakarta.validation.ConstraintViolationException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record FieldError(String field, String message) {}
    public record ErrorResponse(String code, String message, List<FieldError> fieldErrors, String requestId) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        ErrorCode code = exception.errorCode();
        return ResponseEntity.status(code.status()).body(
            new ErrorResponse(code.name(), exception.getMessage(), List.of(), RequestIdHolder.currentOrRandom())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
            .toList();
        return ResponseEntity.badRequest().body(
            new ErrorResponse("INVALID_REQUEST", "요청값을 확인해주세요.", errors, RequestIdHolder.currentOrRandom())
        );
    }

    /** @RequestParam / @PathVariable 검증 실패 (Spring 메서드 검증) → 400 + fieldErrors */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException exception) {
        List<FieldError> errors = exception.getParameterValidationResults().stream()
            .flatMap(r -> r.getResolvableErrors().stream()
                .map(err -> new FieldError(r.getMethodParameter().getParameterName(), err.getDefaultMessage())))
            .toList();
        return ResponseEntity.badRequest().body(
            new ErrorResponse("INVALID_REQUEST", "요청값을 확인해주세요.", errors, RequestIdHolder.currentOrRandom())
        );
    }

    /** @Validated 클래스 레벨 메서드 검증 실패 → 400 + fieldErrors */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        List<FieldError> errors = exception.getConstraintViolations().stream()
            .map(v -> {
                String path = v.getPropertyPath().toString();
                String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                return new FieldError(field, v.getMessage());
            })
            .toList();
        return ResponseEntity.badRequest().body(
            new ErrorResponse("INVALID_REQUEST", "요청값을 확인해주세요.", errors, RequestIdHolder.currentOrRandom())
        );
    }

    /** 필수 파라미터 누락 / 타입 불일치 / JSON 파싱 실패 → 400 */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class})
    ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
        String message = switch (exception) {
            case MissingServletRequestParameterException m -> "필수 파라미터가 누락되었습니다: " + m.getParameterName();
            case MethodArgumentTypeMismatchException m -> "파라미터 형식이 올바르지 않습니다: " + m.getName();
            default -> "요청 본문을 읽을 수 없습니다.";
        };
        return ResponseEntity.badRequest().body(
            new ErrorResponse("INVALID_REQUEST", message, List.of(), RequestIdHolder.currentOrRandom())
        );
    }

    /** 존재하지 않는 경로 → 404 */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException exception) {
        ErrorCode code = ErrorCode.NOT_FOUND;
        return ResponseEntity.status(code.status()).body(
            new ErrorResponse(code.name(), code.message(), List.of(), RequestIdHolder.currentOrRandom())
        );
    }

    /** 그 외 → 500 (스택은 로그에만) */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(code.status()).body(
            new ErrorResponse(code.name(), code.message(), List.of(), RequestIdHolder.currentOrRandom())
        );
    }
}
