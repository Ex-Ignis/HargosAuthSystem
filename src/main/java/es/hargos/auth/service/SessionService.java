package es.hargos.auth.service;

import es.hargos.auth.dto.response.SessionResponse;
import es.hargos.auth.entity.UserEntity;
import es.hargos.auth.entity.UserSessionEntity;
import es.hargos.auth.exception.ResourceNotFoundException;
import es.hargos.auth.repository.RefreshTokenRepository;
import es.hargos.auth.repository.UserRepository;
import es.hargos.auth.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
}
