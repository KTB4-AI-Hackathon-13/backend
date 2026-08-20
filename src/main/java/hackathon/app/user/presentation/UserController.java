package hackathon.app.user.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import hackathon.app.common.api.ApiResponse;
import hackathon.app.user.application.UserService;
import hackathon.app.user.domain.User;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service;
    private final boolean cookieSecure;

    public UserController(UserService service, @Value("${app.auth.cookie-secure:true}") boolean cookieSecure) {
        this.service = service;
        this.cookieSecure = cookieSecure;
    }

    public record UserResponse(Long id, String email, String nickname, String profileImageUrl, String timezone) {
        static UserResponse from(User u) { return new UserResponse(u.getId(), u.getEmail(), u.getNickname(), null, u.getTimezone()); }
    }
    public record UpdateRequest(String nickname, Long profileImageId, String timezone) {}
    public record PasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword, @NotBlank String newPasswordConfirmation) {}
    public record WithdrawRequest(String password, String reason) {}

    @GetMapping("/me")
    ApiResponse<UserResponse> me(@CookieValue(name="SESSION", required=false) String sessionId) {
        return ApiResponse.of(UserResponse.from(service.get(sessionId)));
    }
    @PatchMapping("/me")
    ApiResponse<UserResponse> update(@CookieValue(name="SESSION", required=false) String sessionId, @RequestBody UpdateRequest request) {
        return ApiResponse.of(UserResponse.from(service.update(sessionId, request.nickname(), request.profileImageId(), request.timezone())));
    }
    @PatchMapping("/me/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    void password(@CookieValue(name="SESSION", required=false) String sessionId,
                  @Valid @RequestBody PasswordRequest request, HttpServletResponse response) {
        service.changePassword(sessionId, request.currentPassword(), request.newPassword(), request.newPasswordConfirmation());
        expireSessionCookie(response);
    }
    @DeleteMapping("/me") @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdraw(@CookieValue(name="SESSION", required=false) String sessionId,
                  @RequestBody(required=false) WithdrawRequest request, HttpServletResponse response) {
        service.withdraw(sessionId, request == null ? null : request.password());
        expireSessionCookie(response);
    }

    private void expireSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("SESSION", "")
            .httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
