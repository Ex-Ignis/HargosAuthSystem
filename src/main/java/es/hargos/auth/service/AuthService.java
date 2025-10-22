package es.hargos.auth.service;

import com.nimbusds.jwt.JWTClaimsSet;
import es.hargos.auth.dto.request.LoginRequest;
import es.hargos.auth.dto.request.RefreshTokenRequest;
import es.hargos.auth.dto.request.RegisterRequest;
import es.hargos.auth.dto.response.LoginResponse;
import es.hargos.auth.dto.response.TenantRoleResponse;
import es.hargos.auth.dto.response.TokenValidationResponse;
import es.hargos.auth.dto.response.UserResponse;
import es.hargos.auth.entity.RefreshTokenEntity;
import es.hargos.auth.entity.UserEntity;
import es.hargos.auth.entity.UserTenantRoleEntity;
import es.hargos.auth.exception.DuplicateResourceException;
import es.hargos.auth.exception.InvalidCredentialsException;
import es.hargos.auth.repository.UserRepository;
import es.hargos.auth.repository.UserTenantRoleRepository;
import es.hargos.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${jwt.access-token-expiration-ms}")
    private Long accessTokenExpiration;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Usuario o contraseña incorrecto"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Usuario o contraseña incorrecto");
        }

        if (!user.getIsActive()) {
            throw new InvalidCredentialsException("Cuenta Suspendida"); // inactive
        }

        List<UserTenantRoleEntity> userTenantRoles = userTenantRoleRepository.findByUserWithTenantAndApp(user);
        String accessToken = jwtUtil.generateAccessToken(user, userTenantRoles);

        RefreshTokenEntity refreshToken = refreshTokenService.createRefreshToken(user);

        UserResponse userResponse = mapToUserResponse(user, userTenantRoles);

        return new LoginResponse(
                accessToken,
                refreshToken.getToken(),
                accessTokenExpiration / 1000, // Convert to seconds
                userResponse
        );
    }

    @Transactional
    public LoginResponse refreshAccessToken(RefreshTokenRequest request) {
        RefreshTokenEntity refreshToken = refreshTokenService.findByToken(request.getRefreshToken());

        if (!refreshToken.isValid()) {
            throw new InvalidCredentialsException("Refresh Token no valido o expirado");
        }

        UserEntity user = refreshToken.getUser();
        List<UserTenantRoleEntity> userTenantRoles = userTenantRoleRepository.findByUserWithTenantAndApp(user);

        String accessToken = jwtUtil.generateAccessToken(user, userTenantRoles);
        UserResponse userResponse = mapToUserResponse(user, userTenantRoles);

        return new LoginResponse(
                accessToken,
                refreshToken.getToken(),
                accessTokenExpiration / 1000,
                userResponse
        );
    }

    @Transactional
    public void logout(String refreshToken) {
        RefreshTokenEntity token = refreshTokenService.findByToken(refreshToken);
        refreshTokenService.revokeToken(token);
    }

    public TokenValidationResponse validateToken(String token) {
        if (!jwtUtil.validateToken(token)) {
            return new TokenValidationResponse(false, null, null, null, null);
        }

        JWTClaimsSet claims = jwtUtil.getClaimsFromToken(token);
        Long userId = (Long) claims.getClaim("userId");
        String email = (String) claims.getClaim("email");
        String fullName = (String) claims.getClaim("fullName");

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> tenantsData =
                (List<java.util.Map<String, Object>>) claims.getClaim("tenants");

        List<TenantRoleResponse> tenants = tenantsData.stream()
                .map(t -> new TenantRoleResponse(
                        ((Number) t.get("tenantId")).longValue(),
                        (String) t.get("tenantName"),
                        (String) t.get("appName"),
                        (String) t.get("role")
                ))
                .collect(Collectors.toList());

        return new TokenValidationResponse(true, userId, email, fullName, tenants);
    }

    private UserResponse mapToUserResponse(UserEntity user, List<UserTenantRoleEntity> userTenantRoles) {
        List<TenantRoleResponse> tenants = userTenantRoles.stream()
                .map(utr -> new TenantRoleResponse(
                        utr.getTenant().getId(),
                        utr.getTenant().getName(),
                        utr.getTenant().getApp().getName(),
                        utr.getRole()
                ))
                .collect(Collectors.toList());

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getIsActive(),
                user.getEmailVerified(),
                tenants,
                user.getCreatedAt()
        );
    }
}
