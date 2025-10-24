package es.hargos.auth.repository;

import es.hargos.auth.entity.RefreshTokenEntity;
import es.hargos.auth.entity.UserEntity;
import es.hargos.auth.entity.UserSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSessionEntity, Long> {

    /**
     * Encuentra todas las sesiones no revocadas de un usuario
     */
    List<UserSessionEntity> findByUserAndIsRevokedOrderByCreatedAtAsc(UserEntity user, Boolean isRevoked);

    /**
     * Encuentra la sesión asociada a un refresh token
     */
    Optional<UserSessionEntity> findByRefreshToken(RefreshTokenEntity refreshToken);

    /**
     * Cuenta las sesiones activas de un usuario
     * (no revocadas + con actividad reciente)
     */
    @Query("SELECT COUNT(s) FROM UserSessionEntity s " +
           "WHERE s.user = :user " +
           "AND s.isRevoked = false " +
           "AND s.lastActivityAt > :since")
    long countActiveSessionsByUser(@Param("user") UserEntity user, @Param("since") LocalDateTime since);

    /**
     * Encuentra todas las sesiones activas de un usuario
     */
    @Query("SELECT s FROM UserSessionEntity s " +
           "WHERE s.user = :user " +
           "AND s.isRevoked = false " +
           "AND s.lastActivityAt > :since " +
           "ORDER BY s.lastActivityAt DESC")
    List<UserSessionEntity> findActiveSessionsByUser(@Param("user") UserEntity user, @Param("since") LocalDateTime since);

    /**
     * Encuentra la sesión más antigua (por createdAt) de un usuario que no esté revocada
     */
    Optional<UserSessionEntity> findFirstByUserAndIsRevokedOrderByCreatedAtAsc(UserEntity user, Boolean isRevoked);

    /**
     * Encuentra una sesión por el JTI del access token
     * Se usa para verificar si un access token ha sido revocado
     */
    @Query("SELECT s FROM UserSessionEntity s " +
           "WHERE s.accessTokenJti = :jti " +
           "AND s.isRevoked = false")
    Optional<UserSessionEntity> findActiveSessionByJti(@Param("jti") String jti);
}
