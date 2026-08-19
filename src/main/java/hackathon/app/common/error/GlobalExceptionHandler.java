package hackathon.app.common.error;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    public record FieldError(String field, String message) {}
    public record ErrorResponse(String code, String message, List<FieldError> fieldErrors, String requestId) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        ErrorCode code = exception.errorCode();
        return ResponseEntity.status(code.status()).body(
            new ErrorResponse(code.name(), code.message(), List.of(), UUID.randomUUID().toString())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
            .toList();
        return ResponseEntity.badRequest().body(
            new ErrorResponse("INVALID_REQUEST", "요청값을 확인해주세요.", errors, UUID.randomUUID().toString())
        );
    }
}
