package es.hargos.auth.controller;

import es.hargos.auth.entity.TenantEntity;
import es.hargos.auth.exception.ResourceNotFoundException;
import es.hargos.auth.repository.TenantRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para comunicación con aplicaciones externas (droplets).
 * Endpoints para verificar límites de riders.
 */
@RestController
@RequestMapping("/api/ritrack")
@RequiredArgsConstructor
public class RitrackController {

    private final TenantRepository tenantRepository;

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
    public ResponseEntity<TenantInfoResponse> getTenantInfo(@PathVariable Long tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        // Obtener riderLimit si existe configuración de Riders
        Integer riderLimit = null;
        if (tenant.getRidersConfig() != null) {
            riderLimit = tenant.getRidersConfig().getRiderLimit();
        }

        return ResponseEntity.ok(new TenantInfoResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getOrganization().getName(),
                tenant.getApp().getName(),
                tenant.getAccountLimit(),
                riderLimit,
                tenant.getIsActive()
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
}
