package hackathon.app.domain.scheduleitem.entity;

import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import java.util.Locale;

/** AI 계획 결과와 달력에 공통으로 사용하는 작업 유형. */
public enum ScheduleItemType {
    STUDY,
    PRACTICE,
    REVIEW,
    EXERCISE,
    REST,
    ETC;

    public static ScheduleItemType from(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_ITEM_TYPE);
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_ITEM_TYPE);
        }
    }
}
