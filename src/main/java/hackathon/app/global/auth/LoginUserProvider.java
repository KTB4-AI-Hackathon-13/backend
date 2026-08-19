package hackathon.app.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * 요청에서 현재 로그인 사용자를 찾아내는 전략.
 * - 지금: X-User-Id 헤더 기반 임시 구현 ({@link HeaderLoginUserProvider})
 * - 추후: 서버 세션 + HttpOnly 쿠키 기반 구현으로 교체 (1번 회원·인증 도메인)
 */
public interface LoginUserProvider {

    Optional<LoginUserInfo> resolve(HttpServletRequest request);
}
