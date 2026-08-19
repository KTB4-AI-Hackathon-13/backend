package hackathon.app.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    PASSWORD_POLICY_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "비밀번호는 8자 이상 72자 이하여야 합니다."),
    PASSWORD_CONFIRMATION_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "비밀번호 확인이 일치하지 않습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "현재 비밀번호가 일치하지 않습니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 정지된 계정입니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    INVALID_TIMEZONE(HttpStatus.UNPROCESSABLE_ENTITY, "올바르지 않은 시간대입니다."),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."),
    IMAGE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "이미지에 접근할 권한이 없습니다."),
    IMAGE_FILE_REQUIRED(HttpStatus.BAD_REQUEST, "이미지 파일이 필요합니다."),
    IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "이미지는 10MB 이하여야 합니다."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PNG, JPEG, WebP 이미지만 업로드할 수 있습니다."),
    INVALID_IMAGE_FILE(HttpStatus.UNPROCESSABLE_ENTITY, "올바른 이미지 파일이 아닙니다."),
    IMAGE_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 저장 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }
}
