package es.hargos.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity representing a Stripe subscription linked to a tenant.
 * Stores subscription information from Stripe API for billing management.
 */
@Entity
@Table(name = "stripe_subscriptions", schema = "auth")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StripeSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private TenantEntity tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    // ==================== STRIPE IDs ====================

    @Column(name = "stripe_customer_id", nullable = false)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", nullable = false, unique = true)
    private String stripeSubscriptionId;

    @Column(name = "stripe_price_id", nullable = false)
    private String stripePriceId;

    @Column(name = "stripe_product_id")
    private String stripeProductId;

    // ==================== SUBSCRIPTION DETAILS ====================

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private Boolean cancelAtPeriodEnd = false;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    // ==================== BILLING INFORMATION ====================

    @Column(name = "billing_cycle_anchor")
    private LocalDateTime billingCycleAnchor;

    @Column(name = "trial_start")
    private LocalDateTime trialStart;

    @Column(name = "trial_end")
    private LocalDateTime trialEnd;

    // ==================== METADATA ====================

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==================== RELATIONSHIPS ====================

    @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL, orphanRemoval = true)
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private Set<StripePaymentHistoryEntity> paymentHistory = new HashSet<>();

    // ==================== LIFECYCLE CALLBACKS ====================

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==================== HELPER METHODS ====================

    /**
     * Check if the subscription is currently active (paid and valid)
     */
    public boolean isActive() {
        return "active".equals(status);
    }

    /**
     * Check if the subscription is in trial period
     */
    public boolean isTrialing() {
        return "trialing".equals(status);
    }

    /**
     * Check if the subscription is past due (payment failed)
     */
    public boolean isPastDue() {
        return "past_due".equals(status);
    }

    /**
     * Check if the subscription will be canceled at the end of the period
     */
    public boolean willCancelSoon() {
        return Boolean.TRUE.equals(cancelAtPeriodEnd);
    }
}
