package es.hargos.auth.util;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validador de contraseñas fuertes
 *
 * Requisitos:
 * - Mínimo 8 caracteres
 * - Al menos 1 letra mayúscula
 * - Al menos 1 letra minúscula
 * - Al menos 1 número
 * - Al menos 1 carácter especial (@#$%^&*()_+-=[]{}|;:,.<>?)
 */
@Component
public class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final String SPECIAL_CHARACTERS = "@#$%^&*()_+-=[]{}|;:,.<>?";

    /**
     * Valida que una contraseña cumpla con los requisitos de seguridad
     *
     * @param password La contraseña a validar
     * @return true si la contraseña es válida, false en caso contrario
     */
    public boolean isValid(String password) {
        return getValidationErrors(password).isEmpty();
    }

    /**
     * Obtiene una lista de errores de validación para una contraseña
     *
     * @param password La contraseña a validar
     * @return Lista de mensajes de error (vacía si la contraseña es válida)
     */
    public List<String> getValidationErrors(String password) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            errors.add("La contraseña no puede estar vacía");
            return errors;
        }

        // Verificar longitud mínima
        if (password.length() < MIN_LENGTH) {
            errors.add("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres");
        }

        // Verificar al menos una mayúscula
        if (!password.chars().anyMatch(Character::isUpperCase)) {
            errors.add("La contraseña debe contener al menos una letra mayúscula");
        }

        // Verificar al menos una minúscula
        if (!password.chars().anyMatch(Character::isLowerCase)) {
            errors.add("La contraseña debe contener al menos una letra minúscula");
        }

        // Verificar al menos un número
        if (!password.chars().anyMatch(Character::isDigit)) {
            errors.add("La contraseña debe contener al menos un número");
        }

        // Verificar al menos un carácter especial
        if (!containsSpecialCharacter(password)) {
            errors.add("La contraseña debe contener al menos un carácter especial (" + SPECIAL_CHARACTERS + ")");
        }

        return errors;
    }

    /**
     * Genera un mensaje de error amigable con todos los requisitos no cumplidos
     *
     * @param password La contraseña a validar
     * @return Mensaje de error o null si la contraseña es válida
     */
    public String getValidationMessage(String password) {
        List<String> errors = getValidationErrors(password);

        if (errors.isEmpty()) {
            return null;
        }

        if (errors.size() == 1) {
            return errors.get(0);
        }

        StringBuilder message = new StringBuilder("La contraseña no cumple los siguientes requisitos: ");
        for (int i = 0; i < errors.size(); i++) {
            message.append(errors.get(i));
            if (i < errors.size() - 1) {
                message.append("; ");
            }
        }

        return message.toString();
    }

    /**
     * Verifica si la contraseña contiene al menos un carácter especial
     */
    private boolean containsSpecialCharacter(String password) {
        return password.chars().anyMatch(c -> SPECIAL_CHARACTERS.indexOf(c) >= 0);
    }
}
