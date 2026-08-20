package hackathon.app.auth.presentation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import hackathon.app.auth.application.AuthService;
import hackathon.app.common.api.ApiResponse;
import hackathon.app.user.domain.User;
import hackathon.app.auth.domain.AuthProvider;
import hackathon.app.auth.infrastructure.kakao.KakaoOAuthClient;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String COOKIE = "SESSION";
    private static final String OAUTH_STATE_COOKIE = "OAUTH_STATE";
    private final AuthService service;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final boolean cookieSecure;
    private final String kakaoFrontendRedirectUri;

    public AuthController(AuthService service, KakaoOAuthClient kakaoOAuthClient,
            @Value("${app.auth.cookie-secure:true}") boolean cookieSecure,
            @Value("${app.auth.kakao.frontend-redirect-uri:http://localhost:5173/oauth/callback}") String kakaoFrontendRedirectUri) {
        this.service = service;
        this.kakaoOAuthClient = kakaoOAuthClient;
        this.cookieSecure = cookieSecure;
        this.kakaoFrontendRedirectUri = kakaoFrontendRedirectUri;
    }
    public record SignupRequest(@Email @NotBlank String email, @NotBlank String password,
        @NotBlank String passwordConfirmation, @NotBlank @Size(max=50) String nickname) {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record UserSummary(Long userId, String email, String nickname) {
        static UserSummary from(User user) { return new UserSummary(user.getId(), user.getEmail(), user.getNickname()); }
    }
    @PostMapping("/signup")
    ResponseEntity<ApiResponse<UserSummary>> signup(@Valid @RequestBody SignupRequest request) {
        User user = service.signup(new AuthService.SignupCommand(request.email(), request.password(),
            request.passwordConfirmation(), request.nickname()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(UserSummary.from(user)));
    }
    @PostMapping("/login")
    ApiResponse<UserSummary> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        AuthService.LoginResult result = service.login(request.email(), request.password(), http.getHeader("User-Agent"), http.getRemoteAddr());
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(result.session().getId(), 7 * 24 * 60 * 60).toString());
        return ApiResponse.of(UserSummary.from(result.user()));
    }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@CookieValue(name=COOKIE, required=false) String sessionId, HttpServletResponse response) {
        service.logout(sessionId); response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", 0).toString());
    }
    @GetMapping("/oauth/kakao")
    ResponseEntity<Void> startKakaoLogin(HttpServletResponse response) {
        String state = UUID.randomUUID().toString();
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie(state, 300).toString());
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(kakaoOAuthClient.authorizationUrl(state))).build();
    }
    @GetMapping("/oauth/kakao/callback")
    ResponseEntity<Void> kakaoCallback(@RequestParam String code, @RequestParam String state,
            @CookieValue(name = OAUTH_STATE_COOKIE, required = false) String expectedState,
            HttpServletRequest request, HttpServletResponse response) {
        if (expectedState == null || !Objects.equals(expectedState, state)) {
            throw new ApiException(ErrorCode.INVALID_OAUTH_STATE);
        }
        KakaoOAuthClient.KakaoUser kakao = kakaoOAuthClient.authenticate(code);
        AuthService.LoginResult result = service.oauthLogin(AuthProvider.KAKAO, kakao.providerUserId(),
                kakao.email(), kakao.emailVerified(), kakao.nickname(),
                request.getHeader("User-Agent"), request.getRemoteAddr());
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(result.session().getId(), 7 * 24 * 60 * 60).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie("", 0).toString());
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(kakaoFrontendRedirectUri)).build();
    }
    private ResponseCookie sessionCookie(String value, long maxAge) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(cookieSecure).sameSite("Lax")
            .path("/").maxAge(maxAge).build();
    }
    private ResponseCookie stateCookie(String value, long maxAge) {
        return ResponseCookie.from(OAUTH_STATE_COOKIE, value).httpOnly(true).secure(cookieSecure).sameSite("Lax")
                .path("/api/v1/auth/oauth/kakao/callback").maxAge(maxAge).build();
    }
}
