package es.hargos.auth.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para actualizar las cantidades de una suscripción existente.
 * Permite cambiar el número de cuentas y riders contratados (upgrade/downgrade).
 * El cambio se prorratea automáticamente.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSubscriptionQuantitiesRequest {

    @NotNull(message = "La cantidad de cuentas es requerida")
    @Min(value = 1, message = "Mínimo 1 cuenta de usuario")
    @Max(value = 20, message = "Máximo 20 cuentas de usuario")
    private Integer accountQuantity;

    @NotNull(message = "La cantidad de riders es requerida")
    @Min(value = 50, message = "Mínimo 50 riders")
    @Max(value = 2000, message = "Máximo 2000 riders. Para más de 2000 riders, contacta con nosotros")
    private Integer ridersQuantity;
}
