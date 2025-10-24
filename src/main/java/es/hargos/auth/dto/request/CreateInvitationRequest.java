package es.hargos.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateInvitationRequest {

    @NotNull(message = "El ID del tenant es obligatorio")
    private Long tenantId;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email debe ser válido")
    private String email;

    @NotBlank(message = "El rol es obligatorio")
    @Pattern(regexp = "USER|TENANT_ADMIN", message = "El rol debe ser USER o TENANT_ADMIN")
    private String role;
}
