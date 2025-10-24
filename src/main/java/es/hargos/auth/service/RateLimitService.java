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
 * Estrategia:
 * - Login: 5 intentos por minuto por IP
 * - Si se excede el límite, retorna false
 * - Los buckets se almacenan en cache Caffeine (in-memory) con expiración automática
 */
@Service
@Slf4j
public class RateLimitService {

    // Cache para almacenar los buckets por IP
    // Expiran después de 1 hora de inactividad para no consumir memoria infinita
    private final Cache<String, Bucket> loginBucketCache;

    public RateLimitService() {
        this.loginBucketCache = Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .maximumSize(100_000) // Máximo 100k IPs diferentes en cache
                .build();
    }

    /**
     * Verifica si se puede permitir un intento de login desde una IP específica
     *
     * Límite: 5 intentos por minuto por IP
     *
     * @param ipAddress IP del cliente
     * @return true si se permite el intento, false si excedió el límite
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
     * Crea un bucket con límite de 5 intentos por minuto
     * El bucket se "rellena" a razón de 5 tokens por minuto
     */
    private Bucket createLoginBucket() {
        // Capacidad: 5 tokens (5 intentos)
        // Refill: 5 tokens cada 1 minuto
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Permite resetear manualmente el rate limit de una IP (útil para testing o admin)
     */
    public void resetLoginRateLimit(String ipAddress) {
        loginBucketCache.invalidate(ipAddress);
        log.info("Rate limit reset for IP: {}", ipAddress);
    }

    /**
     * Obtiene estadísticas del cache (útil para monitoreo)
     */
    public long getCacheSize() {
        return loginBucketCache.estimatedSize();
    }
}
