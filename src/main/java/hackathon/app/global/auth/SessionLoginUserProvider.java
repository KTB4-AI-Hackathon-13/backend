package hackathon.app.global.auth;

import hackathon.app.auth.application.AuthService;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.user.domain.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 회원·인증 도메인의 세션 쿠키(SESSION)로 현재 사용자를 식별한다.
 * - 쿠키가 있으면 {@link AuthService#requireUser(String)} 로 검증 (만료/폐기 시 401 AUTHENTICATION_REQUIRED)
 * - 쿠키가 없고 app.auth.dev-header-enabled=true 이면 X-User-Id 헤더로 폴백 (로컬 테스트 전용)
 */
@Component
public class SessionLoginUserProvider implements LoginUserProvider {

    /** AuthController 와 동일한 쿠키 이름 */
    public static final String SESSION_COOKIE = "SESSION";

    private final AuthService authService;
    private final boolean devHeaderEnabled;
    private final HeaderLoginUserProvider headerFallback = new HeaderLoginUserProvider();

    public SessionLoginUserProvider(AuthService authService,
                                    @Value("${app.auth.dev-header-enabled:false}") boolean devHeaderEnabled) {
        this.authService = authService;
        this.devHeaderEnabled = devHeaderEnabled;
    }

    @Override
    public Optional<LoginUserInfo> resolve(HttpServletRequest request) {
        Optional<String> sessionId = sessionId(request);
        if (sessionId.isPresent()) {
            User user = authService.requireUser(sessionId.get());   // 유효하지 않으면 ApiException(401)
            return Optional.of(new LoginUserInfo(user.getId()));
        }
        if (devHeaderEnabled) {
            return headerFallback.resolve(request);
        }
        throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    private Optional<String> sessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> SESSION_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }
}
