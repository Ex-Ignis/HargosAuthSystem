package es.hargos.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AcceptInvitationRequest {

    @NotBlank(message = "El token es obligatorio")
    private String token;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;

    @NotBlank(message = "El nombre completo es obligatorio")
    private String fullName;
}
