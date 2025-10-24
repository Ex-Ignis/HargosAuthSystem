package es.hargos.auth.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTenantRequest {

    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    private String name;

    @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
    private String description;

    @Min(value = 1, message = "El límite de cuentas debe ser al menos 1")
    private Integer accountLimit;
}
