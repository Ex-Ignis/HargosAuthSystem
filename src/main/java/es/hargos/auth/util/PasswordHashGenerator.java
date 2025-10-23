package es.hargos.auth.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utilidad para generar hashes BCrypt de contraseñas.
 *
 * Uso:
 * 1. Ejecuta este main method
 * 2. Cambia la variable PASSWORD por tu contraseña deseada
 * 3. Copia el hash generado en el script init-db.sql o por el hash de SuperAdmin
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // ⚠️ CAMBIAR ESTA CONTRASEÑA
        String password = "SuperAdmin123!";

        String hash = encoder.encode(password);

        System.out.println("=".repeat(70));
        System.out.println("GENERADOR DE HASH BCRYPT");
        System.out.println("=".repeat(70));
        System.out.println();
        System.out.println("Contraseña original: " + password);
        System.out.println();
        System.out.println("Hash BCrypt generado:");
        System.out.println(hash);
        System.out.println();
        System.out.println("Copia este hash y reemplázalo en:");
        System.out.println("init-db.sql o por el hash de SuperAdmin");
        System.out.println();
        System.out.println("En la línea que dice:");
        System.out.println("VALUES ('admin@hargos.es', 'AQUI_VA_EL_HASH', ...)");
        System.out.println();
        System.out.println("=".repeat(70));

        // Generar algunos hashes adicionales para usuarios de prueba
//        System.out.println();
//        System.out.println("Hashes adicionales para usuarios de prueba:");
//        System.out.println("-".repeat(70));
//
//        String[] testPasswords = {
//            "TenantAdmin123!",
//            "User123!"
//        };

//        for (String pwd : testPasswords) {
//            String testHash = encoder.encode(pwd);
//            System.out.println("Password: " + pwd);
//            System.out.println("Hash:     " + testHash);
//            System.out.println();
//        }
    }
}
