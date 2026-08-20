package hackathon.app.auth.service;
import org.springframework.stereotype.Component;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
@Component
public class PasswordPolicy {
    public void validate(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY_VIOLATION);
        }
    }
}
