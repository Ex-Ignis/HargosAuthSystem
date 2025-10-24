package es.hargos.auth.exception;

/**
 * Excepción lanzada cuando se excede el límite de intentos (rate limiting)
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
