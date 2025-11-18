package es.hargos.auth.controller;

import es.hargos.auth.entity.LimitExceededNotificationEntity;
import es.hargos.auth.entity.TenantEntity;
import es.hargos.auth.exception.ResourceNotFoundException;
import es.hargos.auth.repository.LimitExceededNotificationRepository;
import es.hargos.auth.repository.TenantRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Controller para comunicación con aplicaciones externas (droplets).
 * Endpoints para verificar límites de riders.
 */
@RestController
@RequestMapping("/api/ritrack")
@RequiredArgsConstructor
public class RitrackController {

    private static final Logger logger = LoggerFactory.getLogger(RitrackController.class);

    private final TenantRepository tenantRepository;
    private final LimitExceededNotificationRepository notificationRepository;
    private final es.hargos.auth.repository.TenantRidersConfigRepository tenantRidersConfigRepository;

    /**
     * Valida si un tenant puede tener el número de riders especificado.
     * Ritrack llama a este endpoint antes de crear/mostrar riders.
     *
     * @param request Contiene tenantId y currentRiderCount
     * @return Información si está dentro del límite o lo ha excedido
     */
    @PostMapping("/validate-rider-limit")
    public ResponseEntity<ValidateRiderLimitResponse> validateRiderLimit(
            @Valid @RequestBody ValidateRiderLimitRequest request) {

        TenantEntity tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        // Verificar que el tenant tiene configuración de RiTrack
        if (tenant.getRidersConfig() == null) {
            throw new IllegalStateException("El tenant '" + tenant.getName() +
                "' no tiene configuración de RiTrack");
        }

        // Si rider_limit es null = ilimitado
        if (tenant.getRidersConfig().getRiderLimit() == null) {
            return ResponseEntity.ok(new ValidateRiderLimitResponse(
                    true,
                    request.getCurrentRiderCount(),
                    null,
                    null,
                    "Riders ilimitados para este tenant"
            ));
        }

        Integer riderLimit = tenant.getRidersConfig().getRiderLimit();

        // Verificar si está dentro del límite
        boolean withinLimit = request.getCurrentRiderCount() <= riderLimit;
        long remaining = riderLimit - request.getCurrentRiderCount();

        if (!withinLimit) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ValidateRiderLimitResponse(
                            false,
                            request.getCurrentRiderCount(),
                            riderLimit,
                            remaining,
                            "Límite de riders excedido. Máximo permitido: " + riderLimit
                    ));
        }

        // Advertencia si está cerca del límite (>90%)
        String message = "OK";
        if (remaining < riderLimit * 0.1) {
            message = "Advertencia: Cerca del límite. Solo quedan " + remaining + " riders disponibles";
        }

        return ResponseEntity.ok(new ValidateRiderLimitResponse(
                true,
                request.getCurrentRiderCount(),
                riderLimit,
                remaining,
                message
        ));
    }

    /**
     * Obtiene información del tenant incluyendo límites.
     * Útil para que el droplet muestre información al usuario.
     */
    @GetMapping("/tenant-info/{tenantId}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<TenantInfoResponse> getTenantInfo(@PathVariable Long tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        // Obtener riderLimit si existe configuración de Riders
        Integer riderLimit = tenantRidersConfigRepository.findByTenantId(tenantId)
                .map(config -> config.getRiderLimit())
                .orElse(null);

        logger.info("Tenant {} info requested: riderLimit={}", tenantId, riderLimit);

        // Forzar carga de relaciones lazy dentro de transacción
        String organizationName = "N/A";
        String appName = "N/A";

        try {
            organizationName = tenant.getOrganization() != null ? tenant.getOrganization().getName() : "N/A";
            appName = tenant.getApp() != null ? tenant.getApp().getName() : "N/A";
        } catch (Exception e) {
            logger.warn("Could not load lazy relations for tenant {}: {}", tenantId, e.getMessage());
        }

        return ResponseEntity.ok(new TenantInfoResponse(
                tenant.getId(),
                tenant.getName(),
                organizationName,
                appName,
                tenant.getAccountLimit(),
                riderLimit,
                tenant.getIsActive()
        ));
    }

    /**
     * Recibe notificaciones de RiTrack cuando detecta que un tenant excedió el límite de riders.
     * Crea una notificación para SUPER_ADMIN.
     *
     * @param request Detalles del exceso detectado
     * @return Confirmación de notificación creada
     */
    @PostMapping("/report-limit-exceeded")
    public ResponseEntity<ReportLimitExceededResponse> reportLimitExceeded(
            @Valid @RequestBody ReportLimitExceededRequest request) {

        logger.warn("RiTrack reporta límite excedido: tenantId={}, current={}, limit={}, excess={}",
                request.getTenantId(), request.getCurrentCount(), request.getAllowedLimit(), request.getExcessCount());

        // Verificar que el tenant existe
        TenantEntity tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado: " + request.getTenantId()));

        // Crear notificación
        LimitExceededNotificationEntity notification = LimitExceededNotificationEntity.builder()
                .tenant(tenant)
                .currentCount(request.getCurrentCount())
                .allowedLimit(request.getAllowedLimit())
                .excessCount(request.getExcessCount())
                .detectedAt(LocalDateTime.now())
                .isAcknowledged(false)
                .build();

        notificationRepository.save(notification);

        logger.info("Notificación de límite excedido creada: id={}, tenant={}, excess={}",
                notification.getId(), tenant.getName(), notification.getExcessCount());

        return ResponseEntity.ok(new ReportLimitExceededResponse(
                notification.getId(),
                7, // Grace period days
                "Notificación creada correctamente para revisión de SUPER_ADMIN"
        ));
    }

    // ==================== DTOs ====================

    @Data
    public static class ValidateRiderLimitRequest {
        @NotNull(message = "Tenant ID es obligatorio")
        private Long tenantId;

        @NotNull(message = "Número actual de riders es obligatorio")
        @Min(value = 0, message = "El número de riders no puede ser negativo")
        private Integer currentRiderCount;
    }

    public record ValidateRiderLimitResponse(boolean withinLimit, Integer currentCount, Integer limit, Long remaining,
                                             String message) {
    }

    public record TenantInfoResponse(Long id, String name, String organizationName, String appName,
                                     Integer accountLimit, Integer riderLimit, Boolean isActive) {
    }

    @Data
    public static class ReportLimitExceededRequest {
        @NotNull(message = "Tenant ID es obligatorio")
        private Long tenantId;

        @NotNull(message = "Número actual de riders es obligatorio")
        @Min(value = 0, message = "El número de riders no puede ser negativo")
        private Integer currentCount;

        @NotNull(message = "Límite permitido es obligatorio")
        @Min(value = 0, message = "El límite no puede ser negativo")
        private Integer allowedLimit;

        @NotNull(message = "Exceso es obligatorio")
        @Min(value = 1, message = "El exceso debe ser al menos 1")
        private Integer excessCount;
    }

    public record ReportLimitExceededResponse(Long notificationId, Integer gracePeriodDays, String message) {
    }
}
