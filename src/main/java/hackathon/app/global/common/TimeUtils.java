package hackathon.app.global.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** 일시 변환 유틸. DB 는 LocalDateTime(Asia/Seoul 기준)으로 저장, 응답은 ISO 8601 + 오프셋. */
public final class TimeUtils {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    private TimeUtils() {
    }

    public static OffsetDateTime toOffset(LocalDateTime dateTime) {
        return dateTime == null ? null
                : dateTime.truncatedTo(ChronoUnit.SECONDS).atZone(DEFAULT_ZONE).toOffsetDateTime();
    }
}
