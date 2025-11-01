package es.hargos.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignTenantRequest {

    @NotNull(message = "Tenant ID is required")
    private Long tenantId;

    @NotBlank(message = "Role is required")
    private String role;
}
