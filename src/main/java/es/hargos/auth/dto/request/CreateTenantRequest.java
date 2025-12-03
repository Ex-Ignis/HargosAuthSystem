package es.hargos.auth.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTenantRequest {

    @NotNull(message = "App ID es obligatorio")
    private Long appId;

    @NotNull(message = "Organization ID es obligatorio")
    private Long organizationId;

    @NotBlank(message = "Nombre del tenant es obligatorio")
    private String name;

    private String description;

    @NotNull(message = "Limite de cuentas es obligatorio")
    @Min(value = 1, message = "El limite minimo de cuentas es 1")
    private Integer accountLimit;

    // Campos opcionales para RiTrack
    @Min(value = 0, message = "El limite de riders debe ser 0 o mayor")
    private Integer riderLimit;

    // Usuario a asignar como TENANT_ADMIN (opcional)
    private Long adminUserId;
}
