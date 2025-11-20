package es.hargos.auth.repository;

import es.hargos.auth.entity.LimitExceededNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for LimitExceededNotificationEntity.
 *
 * Provides queries for SUPER_ADMIN to monitor tenants that have exceeded rider limits.
 */
@Repository
public interface LimitExceededNotificationRepository extends JpaRepository<LimitExceededNotificationEntity, Long> {

    /**
     * Find all unacknowledged notifications, ordered by most recent first.
     *
     * @return List of unacknowledged notifications
     */
    @Query("SELECT n FROM LimitExceededNotificationEntity n " +
           "WHERE n.isAcknowledged = false " +
           "ORDER BY n.detectedAt DESC")
    List<LimitExceededNotificationEntity> findUnacknowledged();

    /**
     * Find all notifications for a specific tenant, ordered by most recent first.
     *
     * @param tenantId Tenant ID
     * @return List of notifications for the tenant
     */
    @Query("SELECT n FROM LimitExceededNotificationEntity n " +
           "WHERE n.tenant.id = :tenantId " +
           "ORDER BY n.detectedAt DESC")
    List<LimitExceededNotificationEntity> findByTenantId(Long tenantId);

    /**
     * Find unacknowledged notifications for a specific tenant.
     *
     * @param tenantId Tenant ID
     * @return List of unacknowledged notifications for the tenant
     */
    @Query("SELECT n FROM LimitExceededNotificationEntity n " +
           "WHERE n.tenant.id = :tenantId AND n.isAcknowledged = false " +
           "ORDER BY n.detectedAt DESC")
    List<LimitExceededNotificationEntity> findUnacknowledgedByTenantId(Long tenantId);
}
