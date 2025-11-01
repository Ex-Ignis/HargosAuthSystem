package es.hargos.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @Email(message = "Email debe ser válido")
    private String email;

    @Size(min = 2, max = 100, message = "El nombre completo debe tener entre 2 y 100 caracteres")
    private String fullName;

    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password; // Opcional - solo si quiere cambiar contraseña
}
