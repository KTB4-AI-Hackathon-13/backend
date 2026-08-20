package hackathon.app.user.dto;

import hackathon.app.user.entity.User;

public record UserResponse(Long id, String email, String nickname, String profileImageUrl, String timezone) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), null, user.getTimezone());
    }
}
