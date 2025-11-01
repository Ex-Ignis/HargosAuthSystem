package es.hargos.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions", schema = "auth")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refresh_token_id", nullable = false)
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private RefreshTokenEntity refreshToken;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "device_type", length = 20)
    private String deviceType; // web, mobile, desktop, unknown

    @Column(name = "access_token_jti", length = 100)
    private String accessTokenJti; // JWT ID para vincular y revocar access tokens

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_revoked", nullable = false)
    private Boolean isRevoked = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (lastActivityAt == null) {
            lastActivityAt = LocalDateTime.now();
        }
    }

    /**
     * Verifica si la sesión está activa
     * Una sesión está activa si:
     * - No está revocada
     * - El refresh token es válido
     * - Hay actividad reciente (últimos 30 minutos)
     */
    public boolean isActive() {
        if (isRevoked) {
            return false;
        }

        if (refreshToken == null || !refreshToken.isValid()) {
            return false;
        }

        // Sesión activa si hubo actividad en los últimos 30 minutos
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        return lastActivityAt.isAfter(thirtyMinutesAgo);
    }

    /**
     * Actualiza la última actividad al tiempo actual
     */
    public void updateActivity() {
        this.lastActivityAt = LocalDateTime.now();
    }

    /**
     * Detecta el tipo de dispositivo basado en el User-Agent
     */
    public static String detectDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "unknown";
        }

        String ua = userAgent.toLowerCase();

        // Detectar mobile
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone") ||
            ua.contains("ipad") || ua.contains("ipod") || ua.contains("windows phone")) {
            return "mobile";
        }

        // Detectar desktop apps (Electron, etc.)
        if (ua.contains("electron") || ua.contains("nwjs")) {
            return "desktop";
        }

        // Por defecto es web (navegador)
        return "web";
    }
}
