package hackathon.app.user.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import hackathon.app.common.api.ApiResponse;
import hackathon.app.user.dto.ChangePasswordRequest;
import hackathon.app.user.dto.UpdateUserRequest;
import hackathon.app.user.dto.UserResponse;
import hackathon.app.user.dto.WithdrawRequest;
import hackathon.app.user.service.UserService;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/me")
    ApiResponse<UserResponse> me(@CookieValue(name="SESSION", required=false) String sessionId) {
        return ApiResponse.of(UserResponse.from(service.get(sessionId)));
    }
    @PatchMapping("/me")
    ApiResponse<UserResponse> update(@CookieValue(name="SESSION", required=false) String sessionId, @RequestBody UpdateUserRequest request) {
        return ApiResponse.of(UserResponse.from(service.update(sessionId, request.nickname(), request.profileImageId(), request.timezone())));
    }
    @PatchMapping("/me/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    void password(@CookieValue(name="SESSION", required=false) String sessionId,
                  @Valid @RequestBody ChangePasswordRequest request, HttpServletResponse response) {
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
            .httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
