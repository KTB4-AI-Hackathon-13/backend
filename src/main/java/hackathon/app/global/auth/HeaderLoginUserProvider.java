package hackathon.app.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * [임시] X-User-Id 헤더로 사용자를 식별한다.
 * 세션 인증이 구현되면 이 빈을 세션 기반 Provider 로 교체한다.
 */
@Component
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
