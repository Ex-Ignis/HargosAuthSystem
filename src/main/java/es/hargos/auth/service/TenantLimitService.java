package es.hargos.auth.service;

import es.hargos.auth.entity.TenantEntity;
import es.hargos.auth.repository.UserTenantRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio para validar límites de cuentas en tenants
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantLimitService {

    private final UserTenantRoleRepository userTenantRoleRepository;

    /**
     * Valida si un tenant puede agregar más usuarios
     *
     * @param tenant El tenant a validar
     * @throws IllegalStateException si el tenant alcanzó el límite de cuentas
     */
    @Transactional(readOnly = true)
    public void validateCanAddUser(TenantEntity tenant) {
        long currentUsers = userTenantRoleRepository.countByTenant(tenant);

        if (currentUsers >= tenant.getAccountLimit()) {
            log.warn("Tenant {} has reached account limit. Current: {}, Limit: {}",
                    tenant.getId(), currentUsers, tenant.getAccountLimit());

            throw new IllegalStateException(
                String.format(
                    "El tenant '%s' ha alcanzado el límite de cuentas (%d/%d). " +
                    "Por favor, actualiza tu plan para agregar más usuarios.",
                    tenant.getName(),
                    currentUsers,
                    tenant.getAccountLimit()
                )
            );
        }

        log.debug("Tenant {} can add users. Current: {}, Limit: {}",
                tenant.getId(), currentUsers, tenant.getAccountLimit());
    }

    /**
     * Verifica si un tenant puede agregar más usuarios (sin lanzar excepción)
     *
     * @param tenant El tenant a verificar
     * @return true si puede agregar usuarios, false si alcanzó el límite
     */
    @Transactional(readOnly = true)
    public boolean canAddUser(TenantEntity tenant) {
        long currentUsers = userTenantRoleRepository.countByTenant(tenant);
        return currentUsers < tenant.getAccountLimit();
    }

    /**
     * Obtiene el número de espacios disponibles en un tenant
     *
     * @param tenant El tenant
     * @return Número de cuentas disponibles (0 si está lleno)
     */
    @Transactional(readOnly = true)
    public long getAvailableSlots(TenantEntity tenant) {
        long currentUsers = userTenantRoleRepository.countByTenant(tenant);
        long available = tenant.getAccountLimit() - currentUsers;
        return Math.max(0, available);
    }

    /**
     * Obtiene información de uso del tenant
     *
     * @param tenant El tenant
     * @return Objeto con información de uso
     */
    @Transactional(readOnly = true)
    public TenantUsageInfo getUsageInfo(TenantEntity tenant) {
        long currentUsers = userTenantRoleRepository.countByTenant(tenant);
        long limit = tenant.getAccountLimit();
        long available = Math.max(0, limit - currentUsers);
        double usagePercentage = (double) currentUsers / limit * 100;

        return new TenantUsageInfo(
            currentUsers,
            limit,
            available,
            usagePercentage,
            currentUsers >= limit
        );
    }

    /**
     * DTO con información de uso del tenant
     */
    public static class TenantUsageInfo {
        public final long currentUsers;
        public final long accountLimit;
        public final long availableSlots;
        public final double usagePercentage;
        public final boolean isFull;

        public TenantUsageInfo(long currentUsers, long accountLimit, long availableSlots,
                               double usagePercentage, boolean isFull) {
            this.currentUsers = currentUsers;
            this.accountLimit = accountLimit;
            this.availableSlots = availableSlots;
            this.usagePercentage = usagePercentage;
            this.isFull = isFull;
        }
    }
}
