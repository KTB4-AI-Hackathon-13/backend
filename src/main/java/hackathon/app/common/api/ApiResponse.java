package hackathon.app.common.api;

import hackathon.app.global.common.RequestIdHolder;

/**
 * 공통 성공 응답. {"data": {...}, "meta": {"requestId": "..."}}
 * requestId 는 RequestIdFilter 가 요청마다 부여한 값 (응답 헤더 X-Request-Id 와 동일).
 */
public record ApiResponse<T>(T data, Meta meta) {
    public record Meta(String requestId) {}

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, new Meta(RequestIdHolder.currentOrRandom()));
    }
}
