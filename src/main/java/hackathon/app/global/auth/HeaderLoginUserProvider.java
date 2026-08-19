package hackathon.app.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * X-User-Id 헤더로 사용자를 식별한다.
 * 빈으로 등록하지 않는다 — 운영 인증은 {@link SessionLoginUserProvider}(세션 쿠키) 이며,
 * 이 클래스는 (1) app.auth.dev-header-enabled=true 일 때 세션 쿠키가 없는 요청의 로컬 테스트 폴백,
 * (2) 컨트롤러 슬라이스 테스트(@WebMvcTest 에서 @Import) 용도로만 쓴다.
 */
public class HeaderLoginUserProvider implements LoginUserProvider {

    public static final String HEADER = "X-User-Id";

    @Override
    public Optional<LoginUserInfo> resolve(HttpServletRequest request) {
        String raw = request.getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new LoginUserInfo(Long.parseLong(raw.trim())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
