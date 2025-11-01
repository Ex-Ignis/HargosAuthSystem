package es.hargos.auth.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Email es obligatorio")
    @Email(message = "Email debe ser valido")
    private String email;

    @NotBlank(message = "Contraseña es obligatorio")
    @Size(min = 6, message = "Contraseña debe tener un tamaño mínimo de 6")
    private String password;

    @NotBlank(message = "Nombre Completo es obligatorio")
    private String fullName;

    @NotEmpty(message = "Debe asignar al menos un tenant")
    @Valid
    private List<TenantRoleAssignment> tenantRoles;

    @Data
    public static class TenantRoleAssignment {
        @jakarta.validation.constraints.NotNull(message = "Tenant ID es obligatorio")
        private Long tenantId;

        @NotBlank(message = "Rol es obligatorio")
        private String role; // TENANT_ADMIN or USER
    }
}
