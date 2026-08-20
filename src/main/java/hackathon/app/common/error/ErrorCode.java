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
    SCHEDULE_PERIOD_TOO_LONG(HttpStatus.UNPROCESSABLE_ENTITY, "스케줄 기간은 최대 30일까지 설정할 수 있습니다."),
    DATE_OUTSIDE_SCHEDULE_PERIOD(HttpStatus.UNPROCESSABLE_ENTITY, "작업 날짜가 스케줄 기간 밖입니다."),
    MAX_DAILY_TASKS_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "하루 최대 작업 수를 초과했습니다."),
    ITEMS_OUTSIDE_SCHEDULE_PERIOD(HttpStatus.CONFLICT, "변경하려는 기간 밖에 작업이 존재합니다."),
    INVALID_ESTIMATED_MINUTES(HttpStatus.UNPROCESSABLE_ENTITY, "소요시간은 1~1440분이어야 합니다."),
    INVALID_ITEM_TYPE(HttpStatus.UNPROCESSABLE_ENTITY, "정의되지 않은 작업 유형입니다."),
    SCHEDULE_NOT_DRAFT(HttpStatus.CONFLICT, "DRAFT 상태의 스케줄만 확정할 수 있습니다."),

    // ===== 7. 퍼즐 API =====
    PUZZLE_NOT_FOUND(HttpStatus.NOT_FOUND, "퍼즐을 찾을 수 없습니다."),
    PUZZLE_NOT_PUBLIC(HttpStatus.FORBIDDEN, "비공개 퍼즐입니다."),
    PUZZLE_NOT_COMPLETED(HttpStatus.UNPROCESSABLE_ENTITY, "완성된 퍼즐만 공개할 수 있습니다."),

    // ===== AI 계획 생성 API =====
    GENERATION_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 생성 작업을 찾을 수 없습니다."),
    GENERATION_ALREADY_RUNNING(HttpStatus.CONFLICT, "동일한 AI 생성 요청이 처리 중입니다."),
    AI_GENERATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AI 계획 생성에 실패했습니다."),

    // ===== 대화 API =====
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "대화방을 찾을 수 없습니다."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없습니다."),
    CONVERSATION_ARCHIVED(HttpStatus.CONFLICT, "보관된 대화방에는 메시지를 추가할 수 없습니다."),
    INVALID_CONVERSATION_STATUS(HttpStatus.UNPROCESSABLE_ENTITY, "대화방은 ARCHIVED 상태로만 변경할 수 있습니다."),
    PLAN_INFORMATION_INCOMPLETE(HttpStatus.UNPROCESSABLE_ENTITY, "계획 생성에 필요한 정보가 부족합니다."),
    AI_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AI 호출 한도를 초과했습니다."),
    AI_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스에 일시적으로 연결할 수 없습니다."),

    // ===== 이미지 API =====
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."),
    IMAGE_NOT_FOUND_IN_CATEGORY(HttpStatus.NOT_FOUND, "AI가 선택한 카테고리에 등록된 이미지가 없습니다."),
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
