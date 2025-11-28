package es.hargos.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private Long expiresIn; // in seconds
    private UserInfo user;

    // Inner class for user info in login response
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserInfo {
        private Long id;
        private String email;
        private String fullName;
        private String profilePictureUrl;
        private String authProvider;
    }

    // Legacy constructor for backwards compatibility
    public LoginResponse(String accessToken, String refreshToken, Long expiresIn, UserResponse userResponse) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        if (userResponse != null) {
            this.user = UserInfo.builder()
                    .id(userResponse.getId())
                    .email(userResponse.getEmail())
                    .fullName(userResponse.getFullName())
                    .profilePictureUrl(userResponse.getProfilePictureUrl())
                    .authProvider(userResponse.getAuthProvider())
                    .build();
        }
    }
}
