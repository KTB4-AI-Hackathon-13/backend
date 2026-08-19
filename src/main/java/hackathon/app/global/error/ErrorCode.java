package hackathon.app.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 명세서의 오류 코드 정의.
 * 도메인별 오류 코드는 해당 도메인 섹션 주석 아래에 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== 공통 =====
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청 형식입니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "유효하지 않은 커서입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // ===== 5. 스케줄 API / 6. 작업 API =====
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "스케줄을 찾을 수 없습니다."),
    SCHEDULE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다."),
    INVALID_SCHEDULE_PERIOD(HttpStatus.UNPROCESSABLE_CONTENT, "시작일은 종료일보다 늦을 수 없습니다."),
    DATE_OUTSIDE_SCHEDULE_PERIOD(HttpStatus.UNPROCESSABLE_CONTENT, "작업 날짜가 스케줄 기간 밖입니다."),
    MAX_DAILY_TASKS_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "하루 최대 작업 수를 초과했습니다."),
    ITEMS_OUTSIDE_SCHEDULE_PERIOD(HttpStatus.CONFLICT, "변경하려는 기간 밖에 작업이 존재합니다.");

    private final HttpStatus status;
    private final String message;
}
