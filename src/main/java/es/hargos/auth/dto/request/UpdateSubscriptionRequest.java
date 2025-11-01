package es.hargos.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSubscriptionRequest {

    @NotBlank(message = "El nuevo ID del precio de Stripe es requerido")
    private String newPriceId;
}
