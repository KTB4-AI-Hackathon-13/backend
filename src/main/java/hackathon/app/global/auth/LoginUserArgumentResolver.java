package hackathon.app.global.auth;

import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** @LoginUser LoginUserInfo 파라미터를 채워준다. 식별 실패 시 401 AUTHENTICATION_REQUIRED. */
@Component
@RequiredArgsConstructor
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final LoginUserProvider loginUserProvider;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && LoginUserInfo.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        return loginUserProvider.resolve(request)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED));
    }
}
