package es.hargos.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import es.hargos.auth.dto.response.LoginResponse;
import es.hargos.auth.entity.RefreshTokenEntity;
import es.hargos.auth.entity.UserEntity;
import es.hargos.auth.entity.UserSessionEntity;
import es.hargos.auth.entity.UserTenantRoleEntity;
import es.hargos.auth.exception.InvalidCredentialsException;
import es.hargos.auth.repository.UserRepository;
import es.hargos.auth.repository.UserSessionRepository;
import es.hargos.auth.repository.UserTenantRoleRepository;
import es.hargos.auth.util.JwtUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for handling Google OAuth2 authentication.
 * Verifies Google ID tokens and creates/authenticates users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final UserSessionRepository userSessionRepository;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    @Value("${google.oauth2.client-id}")
    private String googleClientId;

    @Value("${jwt.access-token-expiration-ms}")
    private Long accessTokenExpiration;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    public void init() {
        verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
        log.info("GoogleAuthService initialized with client ID: {}...",
                googleClientId.substring(0, Math.min(20, googleClientId.length())));
    }

    /**
     * Authenticates a user using Google ID token.
     * If the user doesn't exist, creates a new account.
     * If the user exists with LOCAL auth, links the Google account.
     *
     * @param idTokenString The Google ID token from frontend
     * @param userAgent User agent string for session tracking
     * @param ipAddress IP address for session tracking
     * @return LoginResponse with JWT tokens
     */
    @Transactional
    public LoginResponse authenticateWithGoogle(String idTokenString, String userAgent, String ipAddress) {
        try {
            // Verify the Google ID token
            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken == null) {
                log.warn("Invalid Google ID token received");
                throw new InvalidCredentialsException("Token de Google inválido");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            // Extract user info from Google token
            String googleId = payload.getSubject();
            String email = payload.getEmail();
            String fullName = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");
            boolean emailVerified = payload.getEmailVerified();

            log.info("Google authentication for email: {}, googleId: {}", email, googleId);

            // Find existing user by Google ID or email
            Optional<UserEntity> existingUserByGoogleId = userRepository.findByGoogleId(googleId);
            Optional<UserEntity> existingUserByEmail = userRepository.findByEmail(email);

            UserEntity user;

            if (existingUserByGoogleId.isPresent()) {
                // User already linked with Google - just login
                user = existingUserByGoogleId.get();
                log.info("Existing Google user found: {}", user.getEmail());

                // Update profile picture if changed
                if (pictureUrl != null && !pictureUrl.equals(user.getProfilePictureUrl())) {
                    user.setProfilePictureUrl(pictureUrl);
                    userRepository.save(user);
                }

            } else if (existingUserByEmail.isPresent()) {
                // User exists with this email but not linked to Google - link it
                user = existingUserByEmail.get();
                log.info("Linking existing account {} to Google ID: {}", email, googleId);

                user.setGoogleId(googleId);
                user.setProfilePictureUrl(pictureUrl);
                // Keep authProvider as is if LOCAL, or update to GOOGLE if they want
                // For now, we'll mark it as LOCAL but with googleId linked
                if (user.getAuthProvider() == null || user.getAuthProvider().equals("LOCAL")) {
                    // User can now login with either method
                    log.info("Account {} now supports both LOCAL and GOOGLE login", email);
                }
                user.setEmailVerified(true); // Google verified the email
                userRepository.save(user);

            } else {
                // New user - create account
                log.info("Creating new user from Google: {}", email);

                user = new UserEntity();
                user.setEmail(email);
                user.setFullName(fullName != null ? fullName : email.split("@")[0]);
                user.setGoogleId(googleId);
                user.setAuthProvider("GOOGLE");
                user.setProfilePictureUrl(pictureUrl);
                user.setEmailVerified(true); // Google verified
                user.setIsActive(true);
                // Set a random password hash (user won't use it, but field is required)
                user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));

                user = userRepository.save(user);
                log.info("New Google user created with ID: {}", user.getId());
            }

            // Check if user is active
            if (!user.getIsActive()) {
                throw new InvalidCredentialsException("Tu cuenta está desactivada");
            }

            // Get user tenant roles for JWT
            List<UserTenantRoleEntity> userTenantRoles = userTenantRoleRepository.findByUserWithTenantAndApp(user);

            // Generate access token with JTI (same pattern as AuthService)
            String[] tokenAndJti = jwtUtil.generateAccessTokenWithJti(user, userTenantRoles);
            String accessToken = tokenAndJti[0];
            String jti = tokenAndJti[1];

            // Generate refresh token
            RefreshTokenEntity refreshToken = refreshTokenService.createRefreshToken(user);

            // Create session (same pattern as AuthService.login())
            UserSessionEntity session = new UserSessionEntity();
            session.setUser(user);
            session.setRefreshToken(refreshToken);
            session.setAccessTokenJti(jti);
            session.setIpAddress(ipAddress);
            session.setUserAgent(userAgent);
            session.setDeviceType(UserSessionEntity.detectDeviceType(userAgent));
            session.setLastActivityAt(LocalDateTime.now());
            session.setIsRevoked(false);
            userSessionRepository.save(session);

            log.info("Google authentication successful for user: {}", user.getEmail());

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken.getToken())
                    .tokenType("Bearer")
                    .expiresIn(accessTokenExpiration / 1000) // Convert to seconds
                    .user(LoginResponse.UserInfo.builder()
                            .id(user.getId())
                            .email(user.getEmail())
                            .fullName(user.getFullName())
                            .profilePictureUrl(user.getProfilePictureUrl())
                            .authProvider(user.getAuthProvider())
                            .build())
                    .build();

        } catch (GeneralSecurityException | IOException e) {
            log.error("Error verifying Google token: {}", e.getMessage());
            throw new InvalidCredentialsException("Error al verificar token de Google");
        }
    }
}
