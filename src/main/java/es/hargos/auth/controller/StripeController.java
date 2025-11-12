package es.hargos.auth.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import es.hargos.auth.dto.request.CreateCheckoutSessionRequest;
import es.hargos.auth.dto.request.CreateOnboardingCheckoutRequest;
import es.hargos.auth.dto.request.UpdateSubscriptionQuantitiesRequest;
import es.hargos.auth.dto.response.CheckoutSessionResponse;
import es.hargos.auth.dto.response.MessageResponse;
import es.hargos.auth.dto.response.PaymentHistoryResponse;
import es.hargos.auth.dto.response.SubscriptionResponse;
import es.hargos.auth.entity.StripePaymentHistoryEntity;
import es.hargos.auth.entity.StripeSubscriptionEntity;
import es.hargos.auth.service.StripeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for Stripe subscription and payment management.
 * Handles checkout, subscription lifecycle, and webhook events.
 */
@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeController {

    private final StripeService stripeService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    // ==================== CHECKOUT ====================

    /**
     * Create a Stripe Checkout Session
     * Permite a cualquier usuario (autenticado o no) contratar el servicio
     * Al completar el pago, se convertirá en TENANT_ADMIN automáticamente
     */
    @PostMapping("/checkout/create-session")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
            @Valid @RequestBody CreateCheckoutSessionRequest request) {

        log.info("Creating checkout session for organization: {}, tenant: {} ({} accounts, {} riders)",
                request.getOrganizationId(), request.getTenantId(),
                request.getAccountQuantity(), request.getRidersQuantity());

        String checkoutUrl = stripeService.createCheckoutSession(
                request.getOrganizationId(),
                request.getTenantId(),
                request.getAccountQuantity(),
                request.getRidersQuantity());

        CheckoutSessionResponse response = new CheckoutSessionResponse(
                checkoutUrl,
                String.format(
                        "Sesión de checkout creada: %d cuentas + %d riders. Total calculado automáticamente por Stripe.",
                        request.getAccountQuantity(), request.getRidersQuantity()));

        return ResponseEntity.ok(response);
    }

    /**
     * Create a Stripe Checkout Session for new customer onboarding
     * This validates tenant creation BEFORE charging, then creates everything on
     * webhook
     *
     * NO authentication required - allows anonymous users to start checkout
     * User will be linked via email stored in metadata
     */
    @PostMapping("/checkout/onboarding")
    public ResponseEntity<CheckoutSessionResponse> createOnboardingCheckoutSession(
            @Valid @RequestBody CreateOnboardingCheckoutRequest request,
            Authentication authentication) {

        String userEmail = authentication != null ? authentication.getName() : null;

        if (userEmail == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new CheckoutSessionResponse(null, "Usuario no autenticado"));
        }

        log.info("Creating onboarding checkout for user: {}, org: {}, tenant: {}",
                userEmail, request.getOrganizationName(), request.getTenantName());

        String checkoutUrl = stripeService.createOnboardingCheckoutSession(
                userEmail,
                request);

        CheckoutSessionResponse response = new CheckoutSessionResponse(
                checkoutUrl,
                String.format("Sesión de onboarding creada: %s - %s (%d cuentas, %d riders)",
                        request.getOrganizationName(),
                        request.getTenantName(),
                        request.getAccountQuantity(),
                        request.getRidersQuantity()));

        return ResponseEntity.ok(response);
    }

    // ==================== SUBSCRIPTION MANAGEMENT ====================

    /**
     * Get subscription details for a tenant
     * Accessible by SUPER_ADMIN or the TENANT_ADMIN of that specific tenant
     */
    @GetMapping("/subscription/tenant/{tenantId}")
    @PreAuthorize("@authz.isSuperAdmin() or @authz.canAccessTenant(#tenantId)")
    public ResponseEntity<SubscriptionResponse> getSubscription(@PathVariable Long tenantId) {

        StripeSubscriptionEntity subscription = stripeService.getSubscriptionByTenantId(tenantId);

        SubscriptionResponse response = SubscriptionResponse.builder()
                .id(subscription.getId())
                .tenantId(subscription.getTenant().getId())
                .tenantName(subscription.getTenant().getName())
                .organizationId(subscription.getOrganization().getId())
                .organizationName(subscription.getOrganization().getName())
                .stripeCustomerId(subscription.getStripeCustomerId())
                .stripeSubscriptionId(subscription.getStripeSubscriptionId())
                .stripePriceId(subscription.getStripePriceId())
                .status(subscription.getStatus())
                .currentPeriodStart(subscription.getCurrentPeriodStart())
                .currentPeriodEnd(subscription.getCurrentPeriodEnd())
                .cancelAtPeriodEnd(subscription.getCancelAtPeriodEnd())
                .canceledAt(subscription.getCanceledAt())
                .createdAt(subscription.getCreatedAt())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Cancel subscription at end of period
     */
    @PostMapping("/subscription/tenant/{tenantId}/cancel")
    @PreAuthorize("@authz.isSuperAdmin() or @authz.canAccessTenant(#tenantId)")
    public ResponseEntity<MessageResponse> cancelSubscription(@PathVariable Long tenantId) {

        log.info("Canceling subscription for tenant: {}", tenantId);
        stripeService.cancelSubscription(tenantId);

        return ResponseEntity.ok(new MessageResponse(
                "Suscripción cancelada. El servicio permanecerá activo hasta el final del período actual."));
    }

    /**
     * Reactivate a subscription that was set to cancel
     */
    @PostMapping("/subscription/tenant/{tenantId}/reactivate")
    @PreAuthorize("@authz.isSuperAdmin() or @authz.canAccessTenant(#tenantId)")
    public ResponseEntity<MessageResponse> reactivateSubscription(@PathVariable Long tenantId) {

        log.info("Reactivating subscription for tenant: {}", tenantId);
        stripeService.reactivateSubscription(tenantId);

        return ResponseEntity.ok(new MessageResponse(
                "Suscripción reactivada correctamente. Se renovará automáticamente al final del período."));
    }

    /**
     * Update subscription quantities (upgrade/downgrade)
     * Allows changing the number of accounts and riders
     */
    @PutMapping("/subscription/tenant/{tenantId}/quantities")
    @PreAuthorize("@authz.isSuperAdmin() or @authz.canAccessTenant(#tenantId)")
    public ResponseEntity<MessageResponse> updateSubscriptionQuantities(
            @PathVariable Long tenantId,
            @Valid @RequestBody UpdateSubscriptionQuantitiesRequest request) {

        log.info("Updating subscription quantities for tenant: {} ({} accounts, {} riders)",
                tenantId, request.getAccountQuantity(), request.getRidersQuantity());

        stripeService.updateSubscriptionQuantities(
                tenantId,
                request.getAccountQuantity(),
                request.getRidersQuantity());

        return ResponseEntity.ok(new MessageResponse(
                String.format(
                        "Cantidades actualizadas: %d cuentas + %d riders. Los cambios se prorratearán automáticamente.",
                        request.getAccountQuantity(), request.getRidersQuantity())));
    }

    // ==================== PAYMENT HISTORY ====================

    /**
     * Get payment history for a tenant
     */
    @GetMapping("/payments/tenant/{tenantId}")
    @PreAuthorize("@authz.isSuperAdmin() or @authz.canAccessTenant(#tenantId)")
    public ResponseEntity<List<PaymentHistoryResponse>> getPaymentHistory(@PathVariable Long tenantId) {

        List<StripePaymentHistoryEntity> payments = stripeService.getPaymentHistory(tenantId);

        List<PaymentHistoryResponse> response = payments.stream()
                .map(payment -> PaymentHistoryResponse.builder()
                        .id(payment.getId())
                        .stripeInvoiceId(payment.getStripeInvoiceId())
                        .amountCents(payment.getAmountCents())
                        .amountEuros(payment.getAmountEuros())
                        .currency(payment.getCurrency())
                        .status(payment.getStatus())
                        .invoicePdfUrl(payment.getInvoicePdfUrl())
                        .hostedInvoiceUrl(payment.getHostedInvoiceUrl())
                        .paidAt(payment.getPaidAt())
                        .createdAt(payment.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // ==================== WEBHOOKS ====================

    /**
     * Stripe Webhook endpoint
     * Handles events from Stripe (checkout completed, subscription updated,
     * payments, etc.)
     *
     * IMPORTANTE: Este endpoint NO debe tener autenticación JWT
     * La seguridad se maneja mediante la firma de Stripe
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        Event event;
        try {
            // Verify webhook signature
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            log.info("Received Stripe webhook event: {}", event.getType());
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe webhook signature", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            log.error("Error processing Stripe webhook", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid payload");
        }
        // Handle the event
        try {
            switch (event.getType()) {
                case "checkout.session.completed":
                    try {
                        Session session = (Session) event.getDataObjectDeserializer()
                                .deserializeUnsafe();
                        stripeService.handleCheckoutCompleted(session);
                        log.info("Successfully processed checkout.session.completed");
                    } catch (Exception e) {
                        log.error("checkout.session.completed: error deserializing Session object", e);
                    }
                    break;
                case "customer.subscription.updated":
                    try {
                        Subscription subscriptionUpdated = (Subscription) event.getDataObjectDeserializer()
                                .deserializeUnsafe();
                        stripeService.handleSubscriptionUpdated(subscriptionUpdated);
                        log.info("Successfully processed customer.subscription.updated");
                    } catch (Exception e) {
                        log.error("customer.subscription.updated: error deserializing Subscription object", e);
                    }
                    break;
                case "customer.subscription.deleted":
                    try {
                        Subscription subscriptionDeleted = (Subscription) event.getDataObjectDeserializer()
                                .deserializeUnsafe();
                        stripeService.handleSubscriptionDeleted(subscriptionDeleted);
                        log.info("Successfully processed customer.subscription.deleted");
                    } catch (Exception e) {
                        log.error("customer.subscription.deleted: error deserializing Subscription object", e);
                    }
                    break;
                case "invoice.payment_succeeded":
                    try {
                        Invoice invoiceSucceeded = (Invoice) event.getDataObjectDeserializer()
                                .deserializeUnsafe();
                        stripeService.handleInvoicePaymentSucceeded(invoiceSucceeded);
                        log.info("Successfully processed invoice.payment_succeeded");
                    } catch (Exception e) {
                        log.error("invoice.payment_succeeded: error deserializing Invoice object", e);
                    }
                    break;
                case "invoice.payment_failed":
                    try {
                        Invoice invoiceFailed = (Invoice) event.getDataObjectDeserializer()
                                .deserializeUnsafe();
                        stripeService.handleInvoicePaymentFailed(invoiceFailed);
                        log.info("Successfully processed invoice.payment_failed");
                    } catch (Exception e) {
                        log.error("invoice.payment_failed: error deserializing Invoice object", e);
                    }
                    break;
                default:
                    log.info("Unhandled event type: {}", event.getType());
            }
            return ResponseEntity.ok("Webhook processed successfully");
        } catch (Exception e) {
            log.error("Error handling webhook event", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing webhook");
        }
    }
}
