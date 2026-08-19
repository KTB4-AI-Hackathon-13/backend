package hackathon.app.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * 요청에서 현재 로그인 사용자를 찾아내는 전략.
 * - 운영: 세션 쿠키(SESSION) 기반 {@link SessionLoginUserProvider} (회원·인증 도메인 AuthService 사용)
 * - 로컬 테스트: app.auth.dev-header-enabled=true 일 때 X-User-Id 헤더 폴백 ({@link HeaderLoginUserProvider})
 */
public interface LoginUserProvider {

    Optional<LoginUserInfo> resolve(HttpServletRequest request);
}
