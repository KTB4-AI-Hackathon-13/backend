package hackathon.app.auth.application;

import java.util.Objects;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import hackathon.app.auth.domain.AuthSession;
import hackathon.app.auth.domain.UserAuthAccount;
import hackathon.app.auth.domain.AuthProvider;
import hackathon.app.auth.infrastructure.AuthSessionJpaRepository;
import hackathon.app.auth.infrastructure.UserAuthAccountJpaRepository;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.user.domain.User;
import hackathon.app.user.domain.UserRepository;
import hackathon.app.user.domain.UserStatus;

@Service
@Transactional
public class AuthService {
    public record SignupCommand(String email, String password, String confirmation, String nickname) {}
    public record LoginResult(User user, AuthSession session) {}
    private final UserRepository users;
    private final UserAuthAccountJpaRepository accounts;
    private final AuthSessionJpaRepository sessions;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwordPolicy;

    public AuthService(UserRepository users, UserAuthAccountJpaRepository accounts,
                       AuthSessionJpaRepository sessions, PasswordEncoder encoder,
                       PasswordPolicy passwordPolicy) {
        this.users = users;
        this.accounts = accounts;
        this.sessions = sessions;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
    }

    public User signup(SignupCommand command) {
        if (!Objects.equals(command.password(), command.confirmation())) throw new ApiException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        passwordPolicy.validate(command.password());
        if (users.existsByEmail(command.email())) throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        if (users.existsByNickname(command.nickname())) throw new ApiException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        User user = users.save(User.create(command.email(), encoder.encode(command.password()), command.nickname()));
        accounts.save(UserAuthAccount.local(user.getId(), user.getEmail()));
        return user;
    }
    public LoginResult login(String email, String password, String userAgent, String ipAddress) {
        User user = users.findByEmail(email).orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        if (user.getStatus() == UserStatus.SUSPENDED) throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
        if (user.getStatus() != UserStatus.ACTIVE || !encoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        user.login();
        return new LoginResult(user, sessions.save(AuthSession.create(user.getId(), userAgent, ipAddress, 7)));
    }
    public LoginResult oauthLogin(AuthProvider provider, String providerUserId, String email, boolean emailVerified,
                                  String nickname, String userAgent, String ipAddress) {
        Optional<UserAuthAccount> linked = accounts.findByProviderAndProviderUserId(provider, providerUserId);
        User user;
        if (linked.isPresent()) {
            user = users.findById(linked.get().getUserId()).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        } else {
            String resolvedEmail = emailVerified && email != null && !email.isBlank()
                    ? email.trim().toLowerCase()
                    : virtualEmail(provider, providerUserId);
            user = users.findByEmail(resolvedEmail)
                    .orElseGet(() -> users.save(User.create(resolvedEmail, null, uniqueNickname(nickname))));
            accounts.save(UserAuthAccount.oauth(user.getId(), provider, providerUserId));
        }
        if (user.getStatus() == UserStatus.SUSPENDED) throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
        if (user.getStatus() != UserStatus.ACTIVE) throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        user.login();
        return new LoginResult(user, sessions.save(AuthSession.create(user.getId(), userAgent, ipAddress, 7)));
    }

    private String uniqueNickname(String requested) {
        String base = requested == null || requested.isBlank() ? "카카오사용자" : requested.trim();
        if (base.length() > 40) base = base.substring(0, 40);
        String candidate = base;
        int suffix = 1;
        while (users.existsByNickname(candidate)) candidate = base + "_" + suffix++;
        return candidate;
    }
    private String virtualEmail(AuthProvider provider, String providerUserId) {
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new ApiException(ErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
        }
        String safeId = providerUserId.replaceAll("[^A-Za-z0-9_-]", "_");
        return provider.name().toLowerCase() + "_" + safeId + "@oauth.local";
    }
    public void logout(String sessionId) { if (sessionId != null) sessions.findById(sessionId).ifPresent(AuthSession::revoke); }
    @Transactional(readOnly = true)
    public User requireUser(String sessionId) {
        if (sessionId == null) throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
        AuthSession session = sessions.findById(sessionId).filter(AuthSession::isUsable)
            .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED));
        return users.findById(session.getUserId()).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }
    @Transactional(readOnly = true)
    public Optional<User> findUser(String sessionId) {
        if (sessionId == null) return Optional.empty();
        return sessions.findById(sessionId)
            .filter(AuthSession::isUsable)
            .flatMap(session -> users.findById(session.getUserId()));
    }
    public void revokeAllSessions(Long userId) { sessions.findAllByUserId(userId).forEach(AuthSession::revoke); }
    public PasswordEncoder passwordEncoder() { return encoder; }
    public PasswordPolicy passwordPolicy() { return passwordPolicy; }
}
