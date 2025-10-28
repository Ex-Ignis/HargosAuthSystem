package es.hargos.auth.repository;

import es.hargos.auth.entity.OrganizationEntity;
import es.hargos.auth.entity.StripeSubscriptionEntity;
import es.hargos.auth.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StripeSubscriptionRepository extends JpaRepository<StripeSubscriptionEntity, Long> {

    /**
     * Find subscription by tenant
     */
    Optional<StripeSubscriptionEntity> findByTenant(TenantEntity tenant);

    /**
     * Find subscription by tenant ID
     */
    Optional<StripeSubscriptionEntity> findByTenantId(Long tenantId);

    /**
     * Find subscription by Stripe subscription ID
     */
    Optional<StripeSubscriptionEntity> findByStripeSubscriptionId(String stripeSubscriptionId);

    /**
     * Find subscription by Stripe customer ID
     */
    Optional<StripeSubscriptionEntity> findByStripeCustomerId(String stripeCustomerId);

    /**
     * Find all subscriptions by organization
     */
    List<StripeSubscriptionEntity> findByOrganization(OrganizationEntity organization);

    /**
     * Find all subscriptions by status
     */
    List<StripeSubscriptionEntity> findByStatus(String status);

    /**
     * Find all active subscriptions (status = 'active')
     */
    @Query("SELECT s FROM StripeSubscriptionEntity s WHERE s.status = 'active'")
    List<StripeSubscriptionEntity> findAllActive();

    /**
     * Find all subscriptions that will end soon (period ending in the next N days)
     */
    @Query("SELECT s FROM StripeSubscriptionEntity s WHERE s.currentPeriodEnd <= :endDate AND s.status = 'active'")
    List<StripeSubscriptionEntity> findSubscriptionsEndingBefore(LocalDateTime endDate);

    /**
     * Find all subscriptions that are past due
     */
    @Query("SELECT s FROM StripeSubscriptionEntity s WHERE s.status = 'past_due'")
    List<StripeSubscriptionEntity> findAllPastDue();

    /**
     * Find all subscriptions that will be canceled at period end
     */
    @Query("SELECT s FROM StripeSubscriptionEntity s WHERE s.cancelAtPeriodEnd = true AND s.status != 'canceled'")
    List<StripeSubscriptionEntity> findAllPendingCancellation();

    /**
     * Check if tenant has an active subscription
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
           "FROM StripeSubscriptionEntity s " +
           "WHERE s.tenant.id = :tenantId AND s.status IN ('active', 'trialing')")
    boolean hasActiveSubscription(Long tenantId);
}
