package es.hargos.auth.security;

import es.hargos.auth.entity.UserEntity;
import es.hargos.auth.entity.UserTenantRoleEntity;
import es.hargos.auth.repository.UserRepository;
import es.hargos.auth.repository.UserTenantRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for authorization checks.
 * Used with @PreAuthorize annotations in controllers.
 */
@Service("authz")
@RequiredArgsConstructor
public class AuthorizationService {

    private final UserRepository userRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;

    /**
     * Check if the current user has SUPER_ADMIN role in any tenant.
     */
    public boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        String email = auth.getName();
        UserEntity user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return false;
        }

        List<UserTenantRoleEntity> roles = userTenantRoleRepository.findByUser(user);
        return roles.stream().anyMatch(role -> "SUPER_ADMIN".equals(role.getRole()));
    }

    /**
     * Check if the current user has TENANT_ADMIN role in any tenant.
     */
    public boolean isTenantAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        String email = auth.getName();
        UserEntity user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return false;
        }

        List<UserTenantRoleEntity> roles = userTenantRoleRepository.findByUser(user);
        return roles.stream().anyMatch(role -> "TENANT_ADMIN".equals(role.getRole()));
    }

    /**
     * Check if the current user is SUPER_ADMIN or TENANT_ADMIN.
     */
    public boolean isAdmin() {
        return isSuperAdmin() || isTenantAdmin();
    }

    /**
     * Check if the current user has TENANT_ADMIN role for a specific tenant.
     */
    public boolean isTenantAdminOf(Long tenantId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        String email = auth.getName();
        UserEntity user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return false;
        }

        List<UserTenantRoleEntity> roles = userTenantRoleRepository.findByUser(user);
        return roles.stream().anyMatch(role ->
                role.getTenant().getId().equals(tenantId) &&
                "TENANT_ADMIN".equals(role.getRole())
        );
    }

    /**
     * Get list of tenant IDs where the current user is TENANT_ADMIN.
     */
    public List<Long> getManagedTenantIds() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return List.of();
        }

        String email = auth.getName();
        UserEntity user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return List.of();
        }

        List<UserTenantRoleEntity> adminRoles = userTenantRoleRepository.findByUserAndRole(user, "TENANT_ADMIN");
        return adminRoles.stream()
                .map(role -> role.getTenant().getId())
                .collect(Collectors.toList());
    }

    /**
     * Check if current user can access a specific tenant (either SUPER_ADMIN or TENANT_ADMIN of that tenant).
     */
    public boolean canAccessTenant(Long tenantId) {
        return isSuperAdmin() || isTenantAdminOf(tenantId);
    }
}
