package hackathon.app.global.common;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 커서 기반 목록 응답.
 * {"items": [], "nextCursor": "...", "hasNext": true}
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CursorPage<T> {

    private final List<T> items;
    private final String nextCursor;
    private final boolean hasNext;

    public static <T> CursorPage<T> of(List<T> items, String nextCursor, boolean hasNext) {
        return new CursorPage<>(items, hasNext ? nextCursor : null, hasNext);
    }
}
