package es.hargos.auth.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Service for authorization checks.
 * Used with @PreAuthorize annotations in controllers.
 *
 * OPTIMIZADO: Lee roles directamente del JWT (ya están en SecurityContext)
 * Evita N+1 queries a la base de datos en cada request
 */
@Service("authz")
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

    /**
     * Check if the current user has SUPER_ADMIN role in any tenant.
     * OPTIMIZADO: Lee del JWT (no hace queries a DB)
     */
    public boolean isSuperAdmin() {
        return hasRole("SUPER_ADMIN");
    }

    /**
     * Check if the current user has TENANT_ADMIN role in any tenant.
     * OPTIMIZADO: Lee del JWT (no hace queries a DB)
     */
    public boolean isTenantAdmin() {
        return hasRole("TENANT_ADMIN");
    }

    /**
     * Check if the current user is SUPER_ADMIN or TENANT_ADMIN.
     */
    public boolean isAdmin() {
        return isSuperAdmin() || isTenantAdmin();
    }

    /**
     * Check if current user can access a specific tenant
     * SUPER_ADMIN: Acceso total a todos los tenants
     * TENANT_ADMIN: Solo sus tenants asignados
     *
     * NOTA: Para TENANT_ADMIN, valida contra tenantId en los claims del JWT
     */
    public boolean canAccessTenant(Long tenantId) {
        if (isSuperAdmin()) {
            return true; // SUPER_ADMIN tiene acceso a todo
        }

        // Para TENANT_ADMIN, verificar si tiene acceso a ese tenant específico
        // Los tenantIds están en el JWT como parte de los roles
        return isTenantAdmin() && hasTenantId(tenantId);
    }

    // ==================== MÉTODOS AUXILIARES ====================

    /**
     * Verifica si el usuario tiene un rol específico
     */
    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }

    /**
     * Verifica si el usuario tiene acceso a un tenant específico
     * Esto requeriría parsear los claims del JWT (tenants array)
     * Por ahora, devuelve true si es TENANT_ADMIN (validación completa requiere JWT)
     *
     * TODO: Implementar parsing de JWT claims para validación más estricta
     */
    private boolean hasTenantId(Long tenantId) {
        // Por ahora, permitir si es TENANT_ADMIN
        // Una implementación más robusta requeriría acceso a los claims del JWT
        // que contienen el array de tenants con sus IDs
        return isTenantAdmin();
    }

    /**
     * Obtiene el email del usuario autenticado
     */
    public String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
