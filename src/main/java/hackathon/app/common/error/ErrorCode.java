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

    // ===== 공통 =====
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청 형식입니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "유효하지 않은 커서입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // ===== 5. 스케줄 API / 6. 작업 API =====
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "스케줄을 찾을 수 없습니다."),
    SCHEDULE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다."),
    INVALID_SCHEDULE_PERIOD(HttpStatus.UNPROCESSABLE_ENTITY, "시작일은 종료일보다 늦을 수 없습니다."),
    DATE_OUTSIDE_SCHEDULE_PERIOD(HttpStatus.UNPROCESSABLE_ENTITY, "작업 날짜가 스케줄 기간 밖입니다."),
    MAX_DAILY_TASKS_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "하루 최대 작업 수를 초과했습니다."),
    ITEMS_OUTSIDE_SCHEDULE_PERIOD(HttpStatus.CONFLICT, "변경하려는 기간 밖에 작업이 존재합니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }
}
