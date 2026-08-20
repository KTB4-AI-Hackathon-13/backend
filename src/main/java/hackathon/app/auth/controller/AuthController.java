package hackathon.app.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import hackathon.app.auth.dto.LoginRequest;
import hackathon.app.auth.dto.SignupRequest;
import hackathon.app.auth.dto.UserSummaryResponse;
import hackathon.app.auth.service.AuthService;
import hackathon.app.common.api.ApiResponse;
import hackathon.app.user.entity.User;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String COOKIE = "SESSION";
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }
    @PostMapping("/signup")
    ResponseEntity<ApiResponse<UserSummaryResponse>> signup(@Valid @RequestBody SignupRequest request) {
        User user = service.signup(new AuthService.SignupCommand(request.email(), request.password(),
            request.passwordConfirmation(), request.nickname()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(UserSummaryResponse.from(user)));
    }
    @PostMapping("/login")
    ApiResponse<UserSummaryResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        AuthService.LoginResult result = service.login(request.email(), request.password(), http.getHeader("User-Agent"), http.getRemoteAddr());
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(result.session().getId(), 7 * 24 * 60 * 60).toString());
        return ApiResponse.of(UserSummaryResponse.from(result.user()));
    }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@CookieValue(name=COOKIE, required=false) String sessionId, HttpServletResponse response) {
        service.logout(sessionId); response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", 0).toString());
    }
    private ResponseCookie sessionCookie(String value, long maxAge) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(true).sameSite("Lax")
            .path("/").maxAge(maxAge).build();
    }
}
