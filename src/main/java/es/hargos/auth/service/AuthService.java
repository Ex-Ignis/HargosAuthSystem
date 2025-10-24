package es.hargos.auth.service;

import com.nimbusds.jwt.JWTClaimsSet;
import es.hargos.auth.dto.request.AcceptInvitationRequest;
import es.hargos.auth.dto.request.ForgotPasswordRequest;
import es.hargos.auth.dto.request.LoginRequest;
import es.hargos.auth.dto.request.RefreshTokenRequest;
import es.hargos.auth.dto.request.RegisterRequest;
import es.hargos.auth.dto.request.RegisterWithAccessCodeRequest;
import es.hargos.auth.dto.request.ResetPasswordRequest;
import es.hargos.auth.dto.response.LoginResponse;
import es.hargos.auth.dto.response.TenantRoleResponse;
import es.hargos.auth.dto.response.TokenValidationResponse;
import es.hargos.auth.dto.response.UserResponse;
import java.util.ArrayList;
import es.hargos.auth.entity.*;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final InvitationService invitationService;
    private final AccessCodeService accessCodeService;
    private final EmailService emailService;

    @Value("${jwt.access-token-expiration-ms}")
    private Long accessTokenExpiration;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Verificar si el usuario ya existe
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("El email ya está registrado");
        }

        // Crear usuario SIN asignación a ningún tenant
        UserEntity user = new UserEntity();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setIsActive(true);
        user.setEmailVerified(false); // Requiere verificación por email

        user = userRepository.save(user);

        // Devolver usuario sin tenants (lista vacía)
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getIsActive(),
                user.getEmailVerified(),
                new ArrayList<>(), // Sin tenants asignados
                user.getCreatedAt()
        );
    }

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

    /**
     * Registrarse aceptando una invitación por email
     */
    @Transactional
    public UserResponse registerFromInvitation(AcceptInvitationRequest request) {
        // Obtener y validar invitación
        InvitationEntity invitation = invitationService.getInvitationByToken(request.getToken());

        if (!invitation.isValid()) {
            throw new InvalidCredentialsException("La invitación ha expirado o ya fue usada");
        }

        // Verificar si el usuario ya existe
        if (userRepository.existsByEmail(invitation.getEmail())) {
            throw new DuplicateResourceException("El email ya está registrado");
        }

        // Crear usuario
        UserEntity user = new UserEntity();
        user.setEmail(invitation.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setIsActive(true);
        user.setEmailVerified(true); // Email verificado por invitación

        user = userRepository.save(user);

        // Asignar al tenant con el rol de la invitación
        UserTenantRoleEntity userTenantRole = new UserTenantRoleEntity();
        userTenantRole.setUser(user);
        userTenantRole.setTenant(invitation.getTenant());
        userTenantRole.setRole(invitation.getRole());
        userTenantRoleRepository.save(userTenantRole);

        // Marcar invitación como aceptada
        invitationService.markAsAccepted(invitation);

        // Devolver usuario
        List<UserTenantRoleEntity> roles = userTenantRoleRepository.findByUserWithTenantAndApp(user);
        return mapToUserResponse(user, roles);
    }

    /**
     * Registrarse usando un código de acceso
     */
    @Transactional
    public UserResponse registerWithAccessCode(RegisterWithAccessCodeRequest request) {
        // Obtener y validar código de acceso
        AccessCodeEntity accessCode = accessCodeService.getAccessCodeByCode(request.getAccessCode());

        if (!accessCode.isValid()) {
            throw new InvalidCredentialsException("El código de acceso es inválido, ha expirado o alcanzó el límite de usos");
        }

        // Verificar si el usuario ya existe
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("El email ya está registrado");
        }

        // Crear usuario
        UserEntity user = new UserEntity();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setIsActive(true);
        user.setEmailVerified(false); // Email NO verificado con código de acceso

        user = userRepository.save(user);

        // Asignar al tenant con el rol del código
        UserTenantRoleEntity userTenantRole = new UserTenantRoleEntity();
        userTenantRole.setUser(user);
        userTenantRole.setTenant(accessCode.getTenant());
        userTenantRole.setRole(accessCode.getRole());
        userTenantRoleRepository.save(userTenantRole);

        // Incrementar usos del código
        accessCodeService.incrementUses(accessCode);

        // Devolver usuario
        List<UserTenantRoleEntity> roles = userTenantRoleRepository.findByUserWithTenantAndApp(user);
        return mapToUserResponse(user, roles);
    }

    /**
     * Solicitar recuperación de contraseña
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // Buscar usuario por email
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("No se encontró un usuario con ese email"));

        // Generar token único
        String token = UUID.randomUUID().toString();

        // Establecer expiración en 1 hora
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        // Guardar token y expiración en el usuario
        user.setPasswordResetToken(token);
        user.setPasswordResetExpiresAt(expiresAt);
        userRepository.save(user);

        // Enviar email de recuperación
        emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), token);
    }

    /**
     * Restablecer contraseña usando token
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // Buscar usuario por token
        UserEntity user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new InvalidCredentialsException("Token de recuperación inválido o expirado"));

        // Verificar que el token no ha expirado
        if (user.getPasswordResetExpiresAt() == null ||
            user.getPasswordResetExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidCredentialsException("El token de recuperación ha expirado");
        }

        // Actualizar contraseña
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));

        // Limpiar token de recuperación
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);

        userRepository.save(user);
    }
}
