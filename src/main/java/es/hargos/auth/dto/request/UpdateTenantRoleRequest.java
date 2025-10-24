package es.hargos.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateTenantRoleRequest {

    @NotBlank(message = "El rol es obligatorio")
    @Pattern(regexp = "SUPER_ADMIN|TENANT_ADMIN|USER", message = "Rol inválido. Debe ser: SUPER_ADMIN, TENANT_ADMIN o USER")
    private String role;
}
