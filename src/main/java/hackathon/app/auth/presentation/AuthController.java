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

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String COOKIE = "SESSION";
    private final AuthService service;
    private final boolean cookieSecure;

    public AuthController(AuthService service, @Value("${app.auth.cookie-secure:true}") boolean cookieSecure) {
        this.service = service;
        this.cookieSecure = cookieSecure;
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
    private ResponseCookie sessionCookie(String value, long maxAge) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(cookieSecure).sameSite("Lax")
            .path("/").maxAge(maxAge).build();
    }
}
