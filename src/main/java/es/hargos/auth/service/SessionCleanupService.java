package es.hargos.auth.service;

import es.hargos.auth.entity.UserSessionEntity;
import es.hargos.auth.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio para limpieza automática de sesiones expiradas
 *
 * Estrategia de limpieza:
 * - Se ejecuta cada 6 horas (a las 00:00, 06:00, 12:00, 18:00)
 * - Elimina sesiones revocadas con más de 7 días de antigüedad
 * - Esto previene el crecimiento infinito de la base de datos
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionCleanupService {

    private final UserSessionRepository userSessionRepository;

    /**
     * Scheduled task que se ejecuta cada 6 horas
     * Cron: "0 0 *\/6 * * *" = A las 00:00, 06:00, 12:00, 18:00 de cada día
     */
    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void cleanupExpiredSessions() {
        log.info("Starting scheduled cleanup of expired sessions...");

        try {
            // Calcular fecha límite: 7 días atrás desde ahora
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);

            // Buscar todas las sesiones revocadas y antiguas
            List<UserSessionEntity> expiredSessions = userSessionRepository
                    .findAll()
                    .stream()
                    .filter(session -> session.getIsRevoked() && session.getCreatedAt().isBefore(cutoffDate))
                    .toList();

            if (expiredSessions.isEmpty()) {
                log.info("No expired sessions to clean up");
                return;
            }

            // Eliminar sesiones expiradas
            int count = expiredSessions.size();
            userSessionRepository.deleteAll(expiredSessions);

            log.info("Successfully cleaned up {} expired sessions (older than {} days)",
                    count, 7);

        } catch (Exception e) {
            log.error("Error during session cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Método manual para limpieza forzada (puede ser llamado por SUPER_ADMIN)
     *
     * @param daysOld Antigüedad en días para considerar sesiones como limpibles
     * @return Número de sesiones eliminadas
     */
    @Transactional
    public int manualCleanup(int daysOld) {
        log.info("Manual cleanup initiated for sessions older than {} days", daysOld);

        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);

            List<UserSessionEntity> expiredSessions = userSessionRepository
                    .findAll()
                    .stream()
                    .filter(session -> session.getIsRevoked() && session.getCreatedAt().isBefore(cutoffDate))
                    .toList();

            int count = expiredSessions.size();
            if (count > 0) {
                userSessionRepository.deleteAll(expiredSessions);
                log.info("Manual cleanup completed: {} sessions removed", count);
            } else {
                log.info("Manual cleanup completed: No sessions to remove");
            }

            return count;

        } catch (Exception e) {
            log.error("Error during manual session cleanup: {}", e.getMessage(), e);
            throw new RuntimeException("Error en limpieza manual de sesiones", e);
        }
    }

    /**
     * Obtiene estadísticas de sesiones para monitoreo
     */
    public SessionStats getSessionStats() {
        long totalSessions = userSessionRepository.count();
        long activeSessions = userSessionRepository.findAll()
                .stream()
                .filter(session -> !session.getIsRevoked())
                .count();
        long revokedSessions = totalSessions - activeSessions;

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        long oldRevokedSessions = userSessionRepository.findAll()
                .stream()
                .filter(session -> session.getIsRevoked() && session.getCreatedAt().isBefore(sevenDaysAgo))
                .count();

        return new SessionStats(totalSessions, activeSessions, revokedSessions, oldRevokedSessions);
    }

    /**
     * DTO para estadísticas de sesiones
     */
    public record SessionStats(
            long totalSessions,
            long activeSessions,
            long revokedSessions,
            long oldRevokedSessions
    ) {}
}
