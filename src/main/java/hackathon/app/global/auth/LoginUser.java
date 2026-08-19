package hackathon.app.global.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드 파라미터에 붙여 현재 로그인 사용자(LoginUserInfo)를 주입받는다.
 * 인증 방식(세션/쿠키)이 확정되면 {@link LoginUserProvider} 구현체만 교체하면 된다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}
