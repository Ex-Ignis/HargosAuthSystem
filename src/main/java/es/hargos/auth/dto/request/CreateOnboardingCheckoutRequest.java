package es.hargos.auth.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a Stripe checkout session for new customer onboarding.
 * This DTO does NOT require pre-existing organization or tenant IDs.
 * The tenant and organization will be created after successful payment.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOnboardingCheckoutRequest {

    @NotBlank(message = "El nombre de la organización es requerido")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    private String organizationName;

    @NotBlank(message = "El nombre del tenant es requerido")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    private String tenantName;

    private String tenantDescription;

    @NotNull(message = "El ID de la aplicación es requerido")
    private Long appId;

    @NotNull(message = "La cantidad de cuentas es requerida")
    @Min(value = 1, message = "Mínimo 1 cuenta de usuario")
    @Max(value = 20, message = "Máximo 20 cuentas de usuario")
    private Integer accountQuantity;

    @NotNull(message = "La cantidad de riders es requerida")
    @Min(value = 50, message = "Mínimo 50 riders")
    @Max(value = 2000, message = "Máximo 2000 riders. Para más de 2000 riders, contacta con nosotros")
    private Integer ridersQuantity;
}
