package hackathon.app.global.common;

import org.slf4j.MDC;

/** 요청 단위 requestId 보관 (MDC 기반). */
public final class RequestIdHolder {

    public static final String MDC_KEY = "requestId";
    public static final String HEADER = "X-Request-Id";

    private RequestIdHolder() {
    }

    public static String get() {
        return MDC.get(MDC_KEY);
    }

    /** 필터 밖(테스트 등)에서 호출돼 requestId 가 없으면 새로 만든다 */
    public static String currentOrRandom() {
        String id = MDC.get(MDC_KEY);
        return id != null ? id : java.util.UUID.randomUUID().toString();
    }

    public static void set(String requestId) {
        MDC.put(MDC_KEY, requestId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
