package es.hargos.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para respuesta de sesiones en el panel de administracion.
 * Incluye informacion del usuario asociado a la sesion.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSessionResponse {
    private Long id;
    private String ipAddress;
    private String userAgent;
    private String deviceType;
    private LocalDateTime lastActivityAt;
    private LocalDateTime createdAt;
    private Boolean isActive;

    // Informacion del usuario
    private Long userId;
    private String userEmail;
    private String userFullName;

    // Informacion del tenant (si tiene)
    private Long tenantId;
    private String tenantName;
}
