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

        System.out.println("=".repeat(80));
        System.out.println(" ".repeat(25) + "GENERADOR DE HASH BCRYPT");
        System.out.println("=".repeat(80));
        System.out.println();

        // ============================================
        // OPCIÓN 1: Generar hash de UNA contraseña
        // ============================================
        System.out.println("OPCIÓN 1: Hash de contraseña única");
        System.out.println("-".repeat(80));

        // ⚠️ CAMBIAR ESTA CONTRASEÑA
        String singlePassword = "ArendelAdmin123!";
        String singleHash = encoder.encode(singlePassword);

        System.out.println("Contraseña: " + singlePassword);
        System.out.println("Hash BCrypt: " + singleHash);
        System.out.println();

        // ============================================
        // OPCIÓN 2: Generar hashes de MÚLTIPLES contraseñas
        // ============================================
        System.out.println("OPCIÓN 2: Hashes para usuarios de prueba");
        System.out.println("-".repeat(80));

        String[][] testUsers = {
            {"test123", "Para desarrollo/testing rápido"},
            {"Admin123!", "Para SUPER_ADMIN"},
            {"TenantAdmin123!", "Para TENANT_ADMIN"},
            {"User123!", "Para usuarios normales"}
        };

        for (String[] userInfo : testUsers) {
            String pwd = userInfo[0];
            String description = userInfo[1];
            String hash = encoder.encode(pwd);

            System.out.println("Contraseña:  " + pwd);
            System.out.println("Descripción: " + description);
            System.out.println("Hash:        " + hash);
            System.out.println();
        }

        // ============================================
        // INSTRUCCIONES DE USO
        // ============================================
        System.out.println("=".repeat(80));
        System.out.println("INSTRUCCIONES:");
        System.out.println("-".repeat(80));
        System.out.println("1. Copia el hash generado");
        System.out.println("2. Úsalo en el script SQL de inicialización");
        System.out.println("3. Ejemplo en SQL:");
        System.out.println();
        System.out.println("   INSERT INTO users (email, password_hash, full_name, ...)");
        System.out.println("   VALUES (");
        System.out.println("       'admin@hargos.es',");
        System.out.println("       '" + singleHash + "',");
        System.out.println("       'Admin Hargos',");
        System.out.println("       true, true, NOW(), NOW()");
        System.out.println("   );");
        System.out.println();
        System.out.println("=".repeat(80));
    }
}
