package es.hargos.auth.service;

import es.hargos.auth.dto.response.AdminSessionResponse;
import es.hargos.auth.dto.response.SessionResponse;
import es.hargos.auth.entity.UserEntity;
import es.hargos.auth.entity.UserSessionEntity;
import es.hargos.auth.entity.UserTenantRoleEntity;
import es.hargos.auth.exception.ResourceNotFoundException;
import es.hargos.auth.repository.RefreshTokenRepository;
import es.hargos.auth.repository.UserRepository;
import es.hargos.auth.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;

    /**
     * Obtiene todas las sesiones activas del usuario autenticado
     */
    @Transactional(readOnly = true)
    public List<SessionResponse> getUserActiveSessions(String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        List<UserSessionEntity> sessions = userSessionRepository.findActiveSessionsByUser(user, thirtyMinutesAgo);

        return sessions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todas las sesiones del usuario (activas e inactivas)
     */
    @Transactional(readOnly = true)
    public List<SessionResponse> getAllUserSessions(String userEmail) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        List<UserSessionEntity> sessions = userSessionRepository
                .findByUserAndIsRevokedOrderByCreatedAtAsc(user, false);

        return sessions.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Revoca una sesión específica por su ID
     */
    @Transactional
    public void revokeSession(String userEmail, Long sessionId) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        UserSessionEntity session = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada"));

        // Verificar que la sesión pertenece al usuario
        if (!session.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Sesión no encontrada");
        }

        // Revocar sesión
        session.setIsRevoked(true);
        userSessionRepository.save(session);

        // Revocar refresh token asociado
        if (session.getRefreshToken() != null) {
            refreshTokenService.revokeToken(session.getRefreshToken());
        }
    }

    /**
     * Revoca todas las sesiones excepto la actual
     */
    @Transactional
    public void revokeAllOtherSessions(String userEmail, String currentRefreshToken) {
        UserEntity user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        List<UserSessionEntity> sessions = userSessionRepository
                .findByUserAndIsRevokedOrderByCreatedAtAsc(user, false);

        for (UserSessionEntity session : sessions) {
            // No revocar la sesión actual
            if (session.getRefreshToken() != null &&
                !session.getRefreshToken().getToken().equals(currentRefreshToken)) {

                session.setIsRevoked(true);
                userSessionRepository.save(session);

                // Revocar refresh token asociado
                refreshTokenService.revokeToken(session.getRefreshToken());
            }
        }
    }

    private SessionResponse mapToResponse(UserSessionEntity session) {
        SessionResponse response = new SessionResponse();
        response.setId(session.getId());
        response.setIpAddress(session.getIpAddress());
        response.setUserAgent(session.getUserAgent());
        response.setDeviceType(session.getDeviceType());
        response.setLastActivityAt(session.getLastActivityAt());
        response.setCreatedAt(session.getCreatedAt());
        response.setIsActive(session.isActive());
        return response;
    }

    // ==================== ADMIN METHODS ====================

    /**
     * Obtiene todas las sesiones activas del sistema (para SUPER_ADMIN)
     * Una sesion se considera activa si tuvo actividad en los ultimos 30 minutos
     */
    @Transactional(readOnly = true)
    public List<AdminSessionResponse> getAllActiveSessions() {
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        List<UserSessionEntity> sessions = userSessionRepository.findAllActiveSessions(thirtyMinutesAgo);

        return sessions.stream()
                .map(this::mapToAdminResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todas las sesiones no revocadas del sistema (para SUPER_ADMIN)
     */
    @Transactional(readOnly = true)
    public List<AdminSessionResponse> getAllSessions() {
        List<UserSessionEntity> sessions = userSessionRepository.findAllNonRevokedSessions();

        return sessions.stream()
                .map(this::mapToAdminResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene estadisticas de sesiones (para SUPER_ADMIN)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSessionStats() {
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        long activeSessions = userSessionRepository.countAllActiveSessions(thirtyMinutesAgo);
        long totalSessions = userSessionRepository.findAllNonRevokedSessions().size();

        Map<String, Object> stats = new HashMap<>();
        stats.put("activeSessions", activeSessions);
        stats.put("totalSessions", totalSessions);
        return stats;
    }

    /**
     * Revoca una sesion como administrador (sin verificar propietario)
     */
    @Transactional
    public void adminRevokeSession(Long sessionId) {
        UserSessionEntity session = userSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sesion no encontrada"));

        session.setIsRevoked(true);
        userSessionRepository.save(session);

        // Revocar refresh token asociado
        if (session.getRefreshToken() != null) {
            refreshTokenService.revokeToken(session.getRefreshToken());
        }
    }

    /**
     * Revoca todas las sesiones de un usuario especifico (como admin)
     */
    @Transactional
    public int adminRevokeAllUserSessions(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        List<UserSessionEntity> sessions = userSessionRepository
                .findByUserAndIsRevokedOrderByCreatedAtAsc(user, false);

        int count = 0;
        for (UserSessionEntity session : sessions) {
            session.setIsRevoked(true);
            userSessionRepository.save(session);

            if (session.getRefreshToken() != null) {
                refreshTokenService.revokeToken(session.getRefreshToken());
            }
            count++;
        }

        return count;
    }

    private AdminSessionResponse mapToAdminResponse(UserSessionEntity session) {
        AdminSessionResponse response = new AdminSessionResponse();
        response.setId(session.getId());
        response.setIpAddress(session.getIpAddress());
        response.setUserAgent(session.getUserAgent());
        response.setDeviceType(session.getDeviceType());
        response.setLastActivityAt(session.getLastActivityAt());
        response.setCreatedAt(session.getCreatedAt());
        response.setIsActive(session.isActive());

        // Info del usuario
        UserEntity user = session.getUser();
        if (user != null) {
            response.setUserId(user.getId());
            response.setUserEmail(user.getEmail());
            response.setUserFullName(user.getFullName());

            // Obtener primer tenant del usuario (si tiene)
            if (user.getUserTenantRoles() != null && !user.getUserTenantRoles().isEmpty()) {
                UserTenantRoleEntity firstUserTenantRole = user.getUserTenantRoles().iterator().next();
                if (firstUserTenantRole.getTenant() != null) {
                    response.setTenantId(firstUserTenantRole.getTenant().getId());
                    response.setTenantName(firstUserTenantRole.getTenant().getName());
                }
            }
        }

        return response;
    }
}
