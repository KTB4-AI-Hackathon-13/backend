package hackathon.app.global.common;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 공통 성공 응답 래퍼.
 * {"data": {...}, "meta": {"requestId": "..."}}
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private final T data;
    private final Meta meta;

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, new Meta(RequestIdHolder.get()));
    }

    @Getter
    @AllArgsConstructor
    public static class Meta {
        private final String requestId;
    }
}
