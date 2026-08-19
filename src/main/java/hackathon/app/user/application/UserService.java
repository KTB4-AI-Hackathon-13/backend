package hackathon.app.user.application;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import hackathon.app.auth.application.AuthService;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.user.domain.User;
import hackathon.app.user.domain.UserRepository;

@Service
@Transactional
public class UserService {
    private final UserRepository users;
    private final AuthService auth;
    public UserService(UserRepository users, AuthService auth) { this.users = users; this.auth = auth; }
    public User get(String sessionId) { return auth.requireUser(sessionId); }
    public User update(String sessionId, String nickname, Long profileImageId, String timezone) {
        User user = auth.requireUser(sessionId);
        if (nickname != null && users.existsByNicknameAndIdNot(nickname, user.getId())) throw new ApiException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        if (timezone != null) try { ZoneId.of(timezone); } catch (DateTimeException e) { throw new ApiException(ErrorCode.INVALID_TIMEZONE); }
        user.updateProfile(nickname, profileImageId, timezone); return user;
    }
    public void changePassword(String sessionId, String current, String next, String confirmation) {
        User user = auth.requireUser(sessionId);
        if (!auth.passwordEncoder().matches(current, user.getPasswordHash())) throw new ApiException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        if (!Objects.equals(next, confirmation)) throw new ApiException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        auth.passwordPolicy().validate(next);
        user.changePassword(auth.passwordEncoder().encode(next));
        auth.revokeAllSessions(user.getId());
    }
    public void withdraw(String sessionId, String password) {
        User user = auth.requireUser(sessionId);
        if (password != null && !auth.passwordEncoder().matches(password, user.getPasswordHash())) throw new ApiException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        user.withdraw(); auth.revokeAllSessions(user.getId());
    }
}
