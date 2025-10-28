package es.hargos.auth.repository;

import es.hargos.auth.entity.StripePaymentHistoryEntity;
import es.hargos.auth.entity.StripeSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StripePaymentHistoryRepository extends JpaRepository<StripePaymentHistoryEntity, Long> {

    /**
     * Find payment by Stripe invoice ID
     */
    Optional<StripePaymentHistoryEntity> findByStripeInvoiceId(String stripeInvoiceId);

    /**
     * Find all payments for a subscription
     */
    List<StripePaymentHistoryEntity> findBySubscription(StripeSubscriptionEntity subscription);

    /**
     * Find all payments for a subscription ordered by creation date
     */
    List<StripePaymentHistoryEntity> findBySubscriptionOrderByCreatedAtDesc(StripeSubscriptionEntity subscription);

    /**
     * Find all payments by subscription ID
     */
    @Query("SELECT p FROM StripePaymentHistoryEntity p WHERE p.subscription.id = :subscriptionId ORDER BY p.createdAt DESC")
    List<StripePaymentHistoryEntity> findBySubscriptionId(Long subscriptionId);

    /**
     * Find all payments by status
     */
    List<StripePaymentHistoryEntity> findByStatus(String status);

    /**
     * Find all paid invoices for a subscription
     */
    @Query("SELECT p FROM StripePaymentHistoryEntity p WHERE p.subscription.id = :subscriptionId AND p.status = 'paid' ORDER BY p.paidAt DESC")
    List<StripePaymentHistoryEntity> findPaidInvoicesBySubscriptionId(Long subscriptionId);

    /**
     * Find all failed payments in a date range
     */
    @Query("SELECT p FROM StripePaymentHistoryEntity p " +
           "WHERE p.status IN ('void', 'uncollectible') " +
           "AND p.attemptedAt BETWEEN :startDate AND :endDate")
    List<StripePaymentHistoryEntity> findFailedPaymentsBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Get total revenue for a subscription
     */
    @Query("SELECT COALESCE(SUM(p.amountCents), 0) FROM StripePaymentHistoryEntity p " +
           "WHERE p.subscription.id = :subscriptionId AND p.status = 'paid'")
    Long getTotalRevenueBySubscriptionId(Long subscriptionId);

    /**
     * Check if invoice already exists
     */
    boolean existsByStripeInvoiceId(String stripeInvoiceId);
}
