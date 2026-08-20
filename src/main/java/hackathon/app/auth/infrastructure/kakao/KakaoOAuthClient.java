package hackathon.app.auth.infrastructure.kakao;

import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KakaoOAuthClient {
    public record KakaoUser(String providerUserId, String email, boolean emailVerified, String nickname) {}
    private record TokenResponse(String access_token) {}
    private record UserResponse(Long id, KakaoAccount kakao_account) {}
    private record KakaoAccount(String email, Boolean is_email_valid, Boolean is_email_verified, Profile profile) {}
    private record Profile(String nickname) {}

    private final RestClient restClient = RestClient.create();
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public KakaoOAuthClient(@Value("${app.auth.kakao.client-id:}") String clientId,
                            @Value("${app.auth.kakao.client-secret:}") String clientSecret,
                            @Value("${app.auth.kakao.redirect-uri:}") String redirectUri) {
        this.clientId = clientId; this.clientSecret = clientSecret; this.redirectUri = redirectUri;
    }

    public String authorizationUrl(String state) {
        requireConfigured();
        return UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
                .queryParam("response_type", "code").queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri).queryParam("state", state)
                .build().encode().toUriString();
    }

    public KakaoUser authenticate(String code) {
        requireConfigured();
        try {
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code"); form.add("client_id", clientId);
            form.add("redirect_uri", redirectUri); form.add("code", code);
            if (!clientSecret.isBlank()) form.add("client_secret", clientSecret);
            TokenResponse token = restClient.post().uri("https://kauth.kakao.com/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(TokenResponse.class);
            if (token == null || token.access_token() == null) throw unavailable();
            UserResponse response = restClient.get().uri("https://kapi.kakao.com/v2/user/me")
                    .headers(headers -> headers.setBearerAuth(token.access_token())).retrieve().body(UserResponse.class);
            if (response == null || response.id() == null) throw unavailable();
            KakaoAccount account = response.kakao_account();
            String email = account == null ? null : account.email();
            boolean verified = account != null && Boolean.TRUE.equals(account.is_email_valid())
                    && Boolean.TRUE.equals(account.is_email_verified());
            String nickname = account == null || account.profile() == null ? null : account.profile().nickname();
            return new KakaoUser(response.id().toString(), email, verified, nickname);
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private void requireConfigured() { if (clientId.isBlank() || redirectUri.isBlank()) throw unavailable(); }
    private ApiException unavailable() { return new ApiException(ErrorCode.OAUTH_PROVIDER_UNAVAILABLE); }
}
