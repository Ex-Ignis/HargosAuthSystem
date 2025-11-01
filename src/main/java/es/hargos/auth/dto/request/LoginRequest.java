package es.hargos.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email obligatorio")
    @Email(message = "Email debe ser valido")
    private String email;

    @NotBlank(message = "Contraseña obligatoria")
    private String password;

    private Boolean rememberMe = false;
}
