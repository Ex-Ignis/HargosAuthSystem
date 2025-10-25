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
import es.hargos.auth.exception.RateLimitExceededException;
import es.hargos.auth.repository.UserRepository;
import es.hargos.auth.repository.UserSessionRepository;
import es.hargos.auth.repository.UserTenantRoleRepository;
import es.hargos.auth.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
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
    private final UserSessionRepository userSessionRepository;
    private final RefreshTokenService refreshTokenService;
    private final RateLimitService rateLimitService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final InvitationService invitationService;
    private final AccessCodeService accessCodeService;
    private final EmailService emailService;
    private final es.hargos.auth.util.PasswordValidator passwordValidator;
    private final TenantLimitService tenantLimitService;

    @Value("${jwt.access-token-expiration-ms}")
    private Long accessTokenExpiration;

    @Transactional
    public UserResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        // 1. Rate Limiting: Verificar límite de registros por IP
        String clientIp = getClientIp(httpRequest);
        if (!rateLimitService.allowRegisterAttempt(clientIp)) {
            throw new RateLimitExceededException(
                "Demasiados intentos de registro. Por favor, espera antes de volver a intentar."
            );
        }

        // 2. Validar contraseña fuerte
        String passwordError = passwordValidator.getValidationMessage(request.getPassword());
        if (passwordError != null) {
            throw new InvalidCredentialsException(passwordError);
        }

        // 3. Verificar si el usuario ya existe
        // IMPORTANTE: Para prevenir enumeración de usuarios, NO lanzamos excepción aquí
        // En su lugar, devolvemos un UserResponse genérico sin crear cuenta
        if (userRepository.existsByEmail(request.getEmail())) {
            // Devolver respuesta genérica (email ya registrado, pero no lo revelamos)
            // El frontend siempre mostrará: "Registro exitoso. Verifica tu email."
            return new UserResponse(
                    null,  // No revelamos ID
                    request.getEmail(),
                    request.getFullName(),
                    false,  // No activo porque no creamos cuenta
                    false,  // No verificado
                    new ArrayList<>(),
                    LocalDateTime.now()
            );
        }

        // 4. Crear usuario SIN asignación a ningún tenant
        UserEntity user = new UserEntity();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setIsActive(true);
        user.setEmailVerified(false);

        user = userRepository.save(user);

        // 5. Devolver usuario sin tenants (lista vacía)
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
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        // 1. Rate Limiting: Verificar límite de intentos por IP
        String clientIp = getClientIp(httpRequest);
        if (!rateLimitService.allowLoginAttempt(clientIp)) {
            throw new RateLimitExceededException(
                "Demasiados intentos de login. Por favor, espera un momento antes de volver a intentar."
            );
        }

        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Usuario o contraseña incorrecto"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Usuario o contraseña incorrecto");
        }

        if (!user.getIsActive()) {
            throw new InvalidCredentialsException("Cuenta Suspendida"); // inactive
        }

        // Verificar límite de sesiones activas (2 sesiones máximo)
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        long activeSessionCount = userSessionRepository.countActiveSessionsByUser(user, thirtyMinutesAgo);

        // Si ya tiene 2 o más sesiones activas, revocar la más antigua
        if (activeSessionCount >= 2) {
            UserSessionEntity oldestSession = userSessionRepository
                    .findFirstByUserAndIsRevokedOrderByCreatedAtAsc(user, false)
                    .orElse(null);

            if (oldestSession != null) {
                oldestSession.setIsRevoked(true);
                userSessionRepository.save(oldestSession);

                // También revocar el refresh token asociado
                if (oldestSession.getRefreshToken() != null) {
                    refreshTokenService.revokeToken(oldestSession.getRefreshToken());
                }
            }
        }

        List<UserTenantRoleEntity> userTenantRoles = userTenantRoleRepository.findByUserWithTenantAndApp(user);

        // Generar access token con JTI único
        String[] tokenAndJti = jwtUtil.generateAccessTokenWithJti(user, userTenantRoles);
        String accessToken = tokenAndJti[0];
        String jti = tokenAndJti[1];

        RefreshTokenEntity refreshToken = refreshTokenService.createRefreshToken(user);

        // Crear nueva sesión con JTI
        UserSessionEntity session = new UserSessionEntity();
        session.setUser(user);
        session.setRefreshToken(refreshToken);
        session.setAccessTokenJti(jti); // Guardar JTI del access token
        session.setIpAddress(getClientIp(httpRequest));
        session.setUserAgent(httpRequest.getHeader("User-Agent"));
        session.setDeviceType(UserSessionEntity.detectDeviceType(httpRequest.getHeader("User-Agent")));
        session.setLastActivityAt(LocalDateTime.now());
        session.setIsRevoked(false);
        userSessionRepository.save(session);

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

        // Actualizar actividad de la sesión
        userSessionRepository.findByRefreshToken(refreshToken).ifPresent(session -> {
            if (!session.getIsRevoked()) {
                session.updateActivity();
                userSessionRepository.save(session);
            }
        });

        UserEntity user = refreshToken.getUser();
        List<UserTenantRoleEntity> userTenantRoles = userTenantRoleRepository.findByUserWithTenantAndApp(user);

        // Generar nuevo access token con nuevo JTI
        String[] tokenAndJti = jwtUtil.generateAccessTokenWithJti(user, userTenantRoles);
        String accessToken = tokenAndJti[0];
        String jti = tokenAndJti[1];

        // Actualizar JTI en la sesión
        userSessionRepository.findByRefreshToken(refreshToken).ifPresent(session -> {
            if (!session.getIsRevoked()) {
                session.setAccessTokenJti(jti);
                userSessionRepository.save(session);
            }
        });

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

        // Revocar la sesión asociada
        userSessionRepository.findByRefreshToken(token).ifPresent(session -> {
            session.setIsRevoked(true);
            userSessionRepository.save(session);
        });

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
    public UserResponse registerFromInvitation(AcceptInvitationRequest request, HttpServletRequest httpRequest) {
        // 1. Rate Limiting: Verificar límite de registros con invitación por IP
        String clientIp = getClientIp(httpRequest);
        if (!rateLimitService.allowRegisterWithInvitationAttempt(clientIp)) {
            throw new RateLimitExceededException(
                "Demasiados intentos de registro con invitación. Por favor, espera antes de volver a intentar."
            );
        }

        // 2. Validar contraseña fuerte
        String passwordError = passwordValidator.getValidationMessage(request.getPassword());
        if (passwordError != null) {
            throw new InvalidCredentialsException(passwordError);
        }

        // 3. Obtener y validar invitación
        InvitationEntity invitation = invitationService.getInvitationByToken(request.getToken());

        if (!invitation.isValid()) {
            throw new InvalidCredentialsException("La invitación ha expirado o ya fue usada");
        }

        // 4. VALIDACIÓN: Verificar límite de cuentas del tenant
        tenantLimitService.validateCanAddUser(invitation.getTenant());

        // 5. Verificar si el usuario ya existe
        if (userRepository.existsByEmail(invitation.getEmail())) {
            throw new DuplicateResourceException("El email ya está registrado");
        }

        // 5. Crear usuario
        UserEntity user = new UserEntity();
        user.setEmail(invitation.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setIsActive(true);
        user.setEmailVerified(true); // Email verificado por invitación

        user = userRepository.save(user);

        // 6. Asignar al tenant con el rol de la invitación
        UserTenantRoleEntity userTenantRole = new UserTenantRoleEntity();
        userTenantRole.setUser(user);
        userTenantRole.setTenant(invitation.getTenant());
        userTenantRole.setRole(invitation.getRole());
        userTenantRoleRepository.save(userTenantRole);

        // 7. Marcar invitación como aceptada
        invitationService.markAsAccepted(invitation);

        // 8. Devolver usuario
        List<UserTenantRoleEntity> roles = userTenantRoleRepository.findByUserWithTenantAndApp(user);
        return mapToUserResponse(user, roles);
    }

    /**
     * Registrarse usando un código de acceso
     */
    @Transactional
    public UserResponse registerWithAccessCode(RegisterWithAccessCodeRequest request, HttpServletRequest httpRequest) {
        // 1. Rate Limiting: Verificar límite de registros con código de acceso por IP
        String clientIp = getClientIp(httpRequest);
        if (!rateLimitService.allowRegisterWithAccessCodeAttempt(clientIp)) {
            throw new RateLimitExceededException(
                "Demasiados intentos de registro con código de acceso. Por favor, espera antes de volver a intentar."
            );
        }

        // 2. Validar contraseña fuerte
        String passwordError = passwordValidator.getValidationMessage(request.getPassword());
        if (passwordError != null) {
            throw new InvalidCredentialsException(passwordError);
        }

        // 3. Obtener y validar código de acceso
        AccessCodeEntity accessCode = accessCodeService.getAccessCodeByCode(request.getAccessCode());

        if (!accessCode.isValid()) {
            throw new InvalidCredentialsException("El código de acceso es inválido, ha expirado o alcanzó el límite de usos");
        }

        // 4. VALIDACIÓN: Verificar límite de cuentas del tenant
        tenantLimitService.validateCanAddUser(accessCode.getTenant());

        // 5. Verificar si el usuario ya existe
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("El email ya está registrado");
        }

        // 5. Crear usuario
        UserEntity user = new UserEntity();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setIsActive(true);
        user.setEmailVerified(false); // Email NO verificado con código de acceso

        user = userRepository.save(user);

        // 6. Asignar al tenant con el rol del código
        UserTenantRoleEntity userTenantRole = new UserTenantRoleEntity();
        userTenantRole.setUser(user);
        userTenantRole.setTenant(accessCode.getTenant());
        userTenantRole.setRole(accessCode.getRole());
        userTenantRoleRepository.save(userTenantRole);

        // 7. Incrementar usos del código
        accessCodeService.incrementUses(accessCode);

        // 8. Devolver usuario
        List<UserTenantRoleEntity> roles = userTenantRoleRepository.findByUserWithTenantAndApp(user);
        return mapToUserResponse(user, roles);
    }

    /**
     * Solicitar recuperación de contraseña
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        // 1. Rate Limiting por IP: Verificar límite de intentos por IP
        String clientIp = getClientIp(httpRequest);
        if (!rateLimitService.allowForgotPasswordAttemptByIp(clientIp)) {
            throw new RateLimitExceededException(
                "Demasiados intentos de recuperación de contraseña. Por favor, espera antes de volver a intentar."
            );
        }

        // 2. Rate Limiting por Email: Verificar límite de intentos por email
        if (!rateLimitService.allowForgotPasswordAttemptByEmail(request.getEmail())) {
            throw new RateLimitExceededException(
                "Demasiados intentos de recuperación para este email. Por favor, espera antes de volver a intentar."
            );
        }

        // 3. Buscar usuario por email
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("No se encontró un usuario con ese email"));

        // 4. Generar token único
        String token = UUID.randomUUID().toString();

        // 5. Establecer expiración en 1 hora
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        // 6. Guardar token y expiración en el usuario
        user.setPasswordResetToken(token);
        user.setPasswordResetExpiresAt(expiresAt);
        userRepository.save(user);

        // 7. Enviar email de recuperación
        emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), token);
    }

    /**
     * Restablecer contraseña usando token
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // 1. Validar contraseña fuerte
        String passwordError = passwordValidator.getValidationMessage(request.getNewPassword());
        if (passwordError != null) {
            throw new InvalidCredentialsException(passwordError);
        }

        // 2. Buscar usuario por token
        UserEntity user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new InvalidCredentialsException("Token de recuperación inválido o expirado"));

        // 3. Verificar que el token no ha expirado
        if (user.getPasswordResetExpiresAt() == null ||
            user.getPasswordResetExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidCredentialsException("El token de recuperación ha expirado");
        }

        // 4. Actualizar contraseña
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));

        // 5. Limpiar token de recuperación
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);

        userRepository.save(user);
    }

    /**
     * Obtiene la IP del cliente desde el request
     * Considera proxies y load balancers
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Si hay múltiples IPs (proxy chain), tomar la primera
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
