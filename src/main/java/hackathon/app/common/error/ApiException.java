package hackathon.app.common.error;

public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    /** 상황별 상세 메시지가 필요할 때 (예: "2026-08-19 에는 이미 5개의 작업이 있습니다.") */
    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() { return errorCode; }
}
