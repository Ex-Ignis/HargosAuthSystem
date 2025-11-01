package es.hargos.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOrganizationRequest {

    @NotBlank(message = "Nombre de organizacion es obligatorio")
    private String name;

    private String description;
}
