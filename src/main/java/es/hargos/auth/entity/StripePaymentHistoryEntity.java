package es.hargos.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing payment history for Stripe subscriptions.
 * Stores invoice and payment information from Stripe API.
 */
@Entity
@Table(name = "stripe_payment_history", schema = "auth")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StripePaymentHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private StripeSubscriptionEntity subscription;

    // ==================== STRIPE IDs ====================

    @Column(name = "stripe_invoice_id", nullable = false, unique = true)
    private String stripeInvoiceId;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "stripe_charge_id")
    private String stripeChargeId;

    // ==================== PAYMENT DETAILS ====================

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(nullable = false, length = 50)
    private String status;

    // ==================== INVOICE DETAILS ====================

    @Column(name = "invoice_pdf_url", length = 500)
    private String invoicePdfUrl;

    @Column(name = "hosted_invoice_url", length = 500)
    private String hostedInvoiceUrl;

    // ==================== TIMESTAMPS ====================

    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ==================== LIFECYCLE CALLBACKS ====================

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ==================== HELPER METHODS ====================

    /**
     * Check if the invoice has been paid
     */
    public boolean isPaid() {
        return "paid".equals(status);
    }

    /**
     * Check if the payment is still pending
     */
    public boolean isPending() {
        return "open".equals(status);
    }

    /**
     * Check if the payment failed or was voided
     */
    public boolean isFailed() {
        return "void".equals(status) || "uncollectible".equals(status);
    }

    /**
     * Get amount in euros (formatted)
     */
    public Double getAmountEuros() {
        return amountCents / 100.0;
    }
}
