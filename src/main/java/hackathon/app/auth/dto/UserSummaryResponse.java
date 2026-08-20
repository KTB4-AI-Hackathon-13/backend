package hackathon.app.auth.dto;

import hackathon.app.user.entity.User;

public record UserSummaryResponse(Long userId, String email, String nickname) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getEmail(), user.getNickname());
    }
}
