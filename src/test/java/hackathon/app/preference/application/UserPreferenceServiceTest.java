package hackathon.app.preference.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hackathon.app.auth.application.AuthService;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.preference.domain.PuzzleVisibility;
import hackathon.app.preference.domain.UserPreference;
import hackathon.app.preference.domain.UserPreferenceRepository;
import hackathon.app.user.domain.User;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserPreferenceServiceTest {

    private static final String SESSION_ID = "session";
    private static final Long USER_ID = 1L;

    private final UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final UserPreferenceService service = new UserPreferenceService(preferences, auth);
    private UserPreference preference;

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(auth.requireUser(SESSION_ID)).thenReturn(user);
        preference = UserPreference.createDefault(USER_ID);
        when(preferences.findByUserId(USER_ID)).thenReturn(Optional.of(preference));
    }

    @Test
    void privateDefaultVisibilityIsRejectedDuringMvp() {
        UserPreferenceService.UpdateCommand command = new UserPreferenceService.UpdateCommand(
                null, null, null, null, PuzzleVisibility.PRIVATE,
                null, null, null, null, null);

        assertThatThrownBy(() -> service.update(SESSION_ID, command))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(preference.getDefaultPuzzleVisibility()).isEqualTo(PuzzleVisibility.PUBLIC);
    }

    @Test
    void legacyPrivatePreferenceIsNormalizedToPublicOnRead() {
        preference.update(null, null, null, null, PuzzleVisibility.PRIVATE,
                null, null, null, null, null);

        UserPreference result = service.get(SESSION_ID);

        assertThat(result.getDefaultPuzzleVisibility()).isEqualTo(PuzzleVisibility.PUBLIC);
    }
}
