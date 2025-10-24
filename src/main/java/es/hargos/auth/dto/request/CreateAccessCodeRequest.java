package es.hargos.auth.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateAccessCodeRequest {

    @NotNull(message = "El ID del tenant es obligatorio")
    private Long tenantId;

    @NotBlank(message = "El rol es obligatorio")
    @Pattern(regexp = "USER|TENANT_ADMIN", message = "El rol debe ser USER o TENANT_ADMIN")
    private String role;

    @Min(value = 1, message = "El máximo de usos debe ser al menos 1")
    private Integer maxUses; // NULL = ilimitado

    private Integer expiresInDays; // NULL = nunca expira
}
