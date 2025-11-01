package es.hargos.auth.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCheckoutSessionRequest {

    @NotNull(message = "El ID de la organización es requerido")
    private Long organizationId;

    @NotNull(message = "El ID del tenant es requerido")
    private Long tenantId;

    @NotNull(message = "La cantidad de cuentas es requerida")
    @Min(value = 1, message = "Mínimo 1 cuenta de usuario")
    @Max(value = 10, message = "Máximo 10 cuentas de usuario")
    private Integer accountQuantity;

    @NotNull(message = "La cantidad de riders es requerida")
    @Min(value = 50, message = "Mínimo 50 riders")
    @Max(value = 1000, message = "Máximo 1000 riders. Para más de 1000 riders, contacta con nosotros")
    private Integer ridersQuantity;

    // Price IDs de Stripe para los productos (se configurarán en application.properties)
    // No se envían desde el frontend, se usan internamente
}
