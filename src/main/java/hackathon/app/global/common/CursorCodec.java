package hackathon.app.global.common;

import hackathon.app.global.error.BusinessException;
import hackathon.app.global.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 커서 인코딩/디코딩 유틸.
 * 현재는 마지막 행의 id(Long)를 Base64(URL-safe)로 감싼 불투명 문자열을 사용한다.
 */
public final class CursorCodec {

    private CursorCodec() {
    }

    public static String encode(Long id) {
        if (id == null) {
            return null;
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.valueOf(id).getBytes(StandardCharsets.UTF_8));
    }

    public static Long decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Long.parseLong(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
