package es.hargos.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de Rate Limiting usando Bucket4j (Token Bucket Algorithm)
 *
 * Estrategias por endpoint:
 * - Login: 5 intentos por minuto por IP
 * - Register: 30 intentos por 15 minutos por IP
 * - Forgot Password: 3 intentos por hora por IP + 5 por día por email
 * - Register with Invitation: 5 intentos por hora por IP
 * - Register with Access Code: 10 intentos por hora por IP
 *
 * Los buckets se almacenan en cache Caffeine (in-memory) con expiración automática
 */
@Service
@Slf4j
public class RateLimitService {

    // Caches separados por tipo de operación
    private final Cache<String, Bucket> loginBucketCache;
    private final Cache<String, Bucket> registerBucketCache;
    private final Cache<String, Bucket> forgotPasswordIpCache;
    private final Cache<String, Bucket> forgotPasswordEmailCache;
    private final Cache<String, Bucket> registerInvitationCache;
    private final Cache<String, Bucket> registerAccessCodeCache;

    public RateLimitService() {
        // Login: 5 intentos por minuto por IP
        this.loginBucketCache = Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .maximumSize(100_000)
                .build();

        // Register: 30 intentos por 15 minutos por IP
        this.registerBucketCache = Caffeine.newBuilder()
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .maximumSize(50_000)
                .build();

        // Forgot Password por IP: 3 intentos por hora
        this.forgotPasswordIpCache = Caffeine.newBuilder()
                .expireAfterAccess(2, TimeUnit.HOURS)
                .maximumSize(50_000)
                .build();

        // Forgot Password por Email: 5 intentos por día
        this.forgotPasswordEmailCache = Caffeine.newBuilder()
                .expireAfterAccess(25, TimeUnit.HOURS)
                .maximumSize(50_000)
                .build();

        // Register with Invitation: 5 intentos por hora por IP
        this.registerInvitationCache = Caffeine.newBuilder()
                .expireAfterAccess(2, TimeUnit.HOURS)
                .maximumSize(50_000)
                .build();

        // Register with Access Code: 10 intentos por hora por IP
        this.registerAccessCodeCache = Caffeine.newBuilder()
                .expireAfterAccess(2, TimeUnit.HOURS)
                .maximumSize(50_000)
                .build();
    }

    /**
     * Verifica si se puede permitir un intento de login desde una IP específica
     * Límite: 5 intentos por minuto por IP
     */
    public boolean allowLoginAttempt(String ipAddress) {
        Bucket bucket = loginBucketCache.get(ipAddress, key -> createLoginBucket());
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            log.warn("Rate limit exceeded for login attempt from IP: {}", ipAddress);
        }

        return allowed;
    }

    /**
     * Verifica si se puede permitir un intento de registro
     * Límite: 30 intentos por 15 minutos por IP
     */
    public boolean allowRegisterAttempt(String ipAddress) {
        Bucket bucket = registerBucketCache.get(ipAddress, key -> createRegisterBucket());
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            log.warn("Rate limit exceeded for register attempt from IP: {}", ipAddress);
        }

        return allowed;
    }

    /**
     * Verifica si se puede permitir un intento de forgot-password por IP
     * Límite: 3 intentos por hora por IP
     */
    public boolean allowForgotPasswordAttemptByIp(String ipAddress) {
        Bucket bucket = forgotPasswordIpCache.get(ipAddress, key -> createForgotPasswordIpBucket());
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            log.warn("Rate limit exceeded for forgot-password attempt from IP: {}", ipAddress);
        }

        return allowed;
    }

    /**
     * Verifica si se puede permitir un intento de forgot-password por Email
     * Límite: 5 intentos por día por email
     */
    public boolean allowForgotPasswordAttemptByEmail(String email) {
        Bucket bucket = forgotPasswordEmailCache.get(email.toLowerCase(), key -> createForgotPasswordEmailBucket());
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            log.warn("Rate limit exceeded for forgot-password attempt for email: {}", email);
        }

        return allowed;
    }

    /**
     * Verifica si se puede permitir un intento de registro con invitación
     * Límite: 5 intentos por hora por IP
     */
    public boolean allowRegisterWithInvitationAttempt(String ipAddress) {
        Bucket bucket = registerInvitationCache.get(ipAddress, key -> createRegisterInvitationBucket());
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            log.warn("Rate limit exceeded for register-invitation attempt from IP: {}", ipAddress);
        }

        return allowed;
    }

    /**
     * Verifica si se puede permitir un intento de registro con código de acceso
     * Límite: 10 intentos por hora por IP
     */
    public boolean allowRegisterWithAccessCodeAttempt(String ipAddress) {
        Bucket bucket = registerAccessCodeCache.get(ipAddress, key -> createRegisterAccessCodeBucket());
        boolean allowed = bucket.tryConsume(1);

        if (!allowed) {
            log.warn("Rate limit exceeded for register-access-code attempt from IP: {}", ipAddress);
        }

        return allowed;
    }

    // ==================== BUCKET CREATION METHODS ====================

    /**
     * Login: 5 intentos por minuto
     */
    private Bucket createLoginBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Register: 30 intentos por 15 minutos
     */
    private Bucket createRegisterBucket() {
        Bandwidth limit = Bandwidth.classic(30, Refill.intervally(30, Duration.ofMinutes(15)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Forgot Password (por IP): 3 intentos por hora
     */
    private Bucket createForgotPasswordIpBucket() {
        Bandwidth limit = Bandwidth.classic(3, Refill.intervally(3, Duration.ofHours(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Forgot Password (por Email): 5 intentos por día
     */
    private Bucket createForgotPasswordEmailBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofDays(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Register with Invitation: 5 intentos por hora
     */
    private Bucket createRegisterInvitationBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofHours(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Register with Access Code: 10 intentos por hora
     */
    private Bucket createRegisterAccessCodeBucket() {
        Bandwidth limit = Bandwidth.classic(10, Refill.intervally(10, Duration.ofHours(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    // ==================== ADMIN METHODS ====================

    /**
     * Permite resetear manualmente el rate limit de una IP (útil para testing o admin)
     */
    public void resetLoginRateLimit(String ipAddress) {
        loginBucketCache.invalidate(ipAddress);
        log.info("Login rate limit reset for IP: {}", ipAddress);
    }

    /**
     * Obtiene estadísticas totales de cache (útil para monitoreo)
     */
    public long getTotalCacheSize() {
        return loginBucketCache.estimatedSize() +
               registerBucketCache.estimatedSize() +
               forgotPasswordIpCache.estimatedSize() +
               forgotPasswordEmailCache.estimatedSize() +
               registerInvitationCache.estimatedSize() +
               registerAccessCodeCache.estimatedSize();
    }
}
