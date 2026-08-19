package hackathon.app.global.error;

import lombok.Getter;

/** 비즈니스 규칙 위반 예외. ErrorCode 가 HTTP 상태와 코드 문자열을 결정한다. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
