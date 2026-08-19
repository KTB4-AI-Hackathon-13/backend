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

    public static void set(String requestId) {
        MDC.put(MDC_KEY, requestId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
