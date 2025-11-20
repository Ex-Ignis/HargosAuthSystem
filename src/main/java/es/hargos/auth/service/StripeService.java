package es.hargos.auth.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.param.*;
import com.stripe.param.checkout.SessionCreateParams;
import es.hargos.auth.dto.request.CreateOnboardingCheckoutRequest;
import es.hargos.auth.entity.*;
import es.hargos.auth.event.TenantLimitsUpdatedEvent;
import es.hargos.auth.exception.DuplicateResourceException;
import es.hargos.auth.exception.ResourceNotFoundException;
import es.hargos.auth.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Optional;
import java.util.List;
import java.util.Map;

/**
 * Service for managing Stripe subscriptions and payments.
 * Handles checkout sessions, subscription lifecycle, and webhook events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final StripeSubscriptionRepository subscriptionRepository;
    private final StripePaymentHistoryRepository paymentHistoryRepository;
    private final TenantRepository tenantRepository;
    private final OrganizationRepository organizationRepository;
    private final TenantRidersConfigRepository tenantRidersConfigRepository;
    private final UserRepository userRepository;
    private final AppRepository appRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final RestTemplate restTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${stripe.api-key}")
    private String stripeApiKey;

    @Value("${ritrack.base-url}")
    private String ritrackBaseUrl;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    @Value("${stripe.price-id.accounts}")
    private String accountsPriceId;

    @Value("${stripe.price-id.riders}")
    private String ridersPriceId;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
        log.info("Stripe API initialized");
    }

    // ==================== CHECKOUT SESSION ====================

    /**
     * Create a Stripe Checkout Session for a new subscription
     *
     * @param organizationId Organization purchasing the subscription
     * @param tenantId Tenant that will be associated with the subscription
     * @param accountQuantity Number of user accounts (1-10)
     * @param ridersQuantity Number of riders to manage (50-1000)
     * @return Checkout Session URL for frontend redirect
     */
    @Transactional
    public String createCheckoutSession(Long organizationId, Long tenantId, Integer accountQuantity, Integer ridersQuantity) {
        try {
            // Validate organization and tenant
            OrganizationEntity organization = organizationRepository.findById(organizationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Organización no encontrada"));

            TenantEntity tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

            // Check if tenant already has a subscription
            if (subscriptionRepository.findByTenantId(tenantId).isPresent()) {
                throw new IllegalStateException("Este tenant ya tiene una suscripción activa");
            }

            // Get tenant admin email for Stripe customer
            String customerEmail = tenant.getUserTenantRoles().stream()
                    .filter(utr -> "TENANT_ADMIN".equals(utr.getRole()))
                    .findFirst()
                    .map(utr -> utr.getUser().getEmail())
                    .orElse(organization.getName().toLowerCase().replaceAll("\\s+", "") + "@customer.com");

            // Create metadata to track the purchase
            Map<String, String> metadata = new HashMap<>();
            metadata.put("organization_id", organizationId.toString());
            metadata.put("tenant_id", tenantId.toString());
            metadata.put("organization_name", organization.getName());
            metadata.put("tenant_name", tenant.getName());
            metadata.put("account_quantity", accountQuantity.toString());
            metadata.put("riders_quantity", ridersQuantity.toString());

            // Build checkout session params with 2 line items (accounts + riders)
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl)
                    // Line Item 1: User Accounts
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(accountsPriceId)
                                    .setQuantity(accountQuantity.longValue())
                                    .build()
                    )
                    // Line Item 2: Riders
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(ridersPriceId)
                                    .setQuantity(ridersQuantity.longValue())
                                    .build()
                    )
                    .putAllMetadata(metadata)
                    .setCustomerEmail(customerEmail)
                    .setAllowPromotionCodes(true)
                    .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED)
                    // Enable automatic tax calculation
                    .setAutomaticTax(
                            SessionCreateParams.AutomaticTax.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .build();

            // Create session with Stripe API
            Session session = Session.create(params);

            log.info("Created Stripe Checkout Session: {} for tenant: {} ({} accounts, {} riders)",
                     session.getId(), tenantId, accountQuantity, ridersQuantity);

            return session.getUrl();

        } catch (StripeException e) {
            log.error("Error creating Stripe checkout session", e);
            throw new RuntimeException("Error al crear la sesión de pago: " + e.getMessage());
        }
    }

    /**
     * Create a Stripe Checkout Session for onboarding
     * Validates that tenant can be created BEFORE charging the customer
     *
     * @param userEmail Email of the user making the purchase
     * @param request Onboarding details
     * @return Stripe Checkout URL
     */
    @Transactional
    public String createOnboardingCheckoutSession(String userEmail, CreateOnboardingCheckoutRequest request) {
        try {
            // 1. Get and validate user
            UserEntity user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

            // 2. Validate app exists
            AppEntity app = appRepository.findById(request.getAppId())
                    .orElseThrow(() -> new ResourceNotFoundException("Aplicación no encontrada"));

            // 3. Validate organization name can be used
            Optional<OrganizationEntity> existingOrg = organizationRepository.findByName(request.getOrganizationName());
            if (existingOrg.isPresent() && !existingOrg.get().getIsActive()) {
                throw new IllegalStateException("La organización '" + request.getOrganizationName() + "' existe pero está inactiva");
            }

            // 4. Validate tenant doesn't exist
            if (existingOrg.isPresent()) {
                boolean tenantExists = tenantRepository.existsByAppAndOrganizationAndName(
                        app,
                        existingOrg.get(),
                        request.getTenantName()
                );

                if (tenantExists) {
                    throw new DuplicateResourceException(
                            "Ya existe un tenant '" + request.getTenantName() +
                            "' para la app '" + app.getName() +
                            "' en la organización '" + request.getOrganizationName() + "'"
                    );
                }
            }

            // 5. Create metadata to store onboarding information
            Map<String, String> metadata = new HashMap<>();
            metadata.put("onboarding", "true");
            metadata.put("user_id", user.getId().toString());
            metadata.put("user_email", user.getEmail());
            metadata.put("organization_name", request.getOrganizationName());
            metadata.put("tenant_name", request.getTenantName());
            metadata.put("tenant_description", request.getTenantDescription() != null ? request.getTenantDescription() : "");
            metadata.put("app_id", request.getAppId().toString());
            metadata.put("app_name", app.getName());
            metadata.put("account_quantity", request.getAccountQuantity().toString());
            metadata.put("riders_quantity", request.getRidersQuantity().toString());

            // 6. Build checkout session params
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl)
                    // Line Item 1: User Accounts (graduated pricing)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(accountsPriceId)
                                    .setQuantity(request.getAccountQuantity().longValue())
                                    .build()
                    )
                    // Line Item 2: Riders (graduated pricing)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(ridersPriceId)
                                    .setQuantity(request.getRidersQuantity().longValue())
                                    .build()
                    )
                    .putAllMetadata(metadata)
                    .setCustomerEmail(user.getEmail())
                    .setAllowPromotionCodes(true)
                    .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED)
                    // Enable automatic tax calculation
                    .setAutomaticTax(
                            SessionCreateParams.AutomaticTax.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .build();

            // 7. Create session with Stripe API
            Session session = Session.create(params);

            log.info("Created onboarding Stripe Checkout Session: {} for user: {}",
                     session.getId(), userEmail);

            return session.getUrl();

        } catch (StripeException e) {
            log.error("Error creating Stripe onboarding checkout session", e);
            throw new RuntimeException("Error al crear la sesión de pago: " + e.getMessage());
        }
    }

    // ==================== SUBSCRIPTION MANAGEMENT ====================

    /**
     * Handle successful checkout completion
     * Called by webhook when checkout.session.completed event is received
     * Routes to onboarding flow or existing tenant update flow
     */
    @Transactional
    public void handleCheckoutCompleted(Session session) {
        try {
            String subscriptionId = session.getSubscription();
            if (subscriptionId == null) {
                log.warn("Checkout session {} has no subscription", session.getId());
                return;
            }

            Map<String, String> metadata = session.getMetadata();

            // Check if this is an onboarding session (new customer) or update (existing tenant)
            boolean isOnboarding = "true".equals(metadata.get("onboarding"));

            if (isOnboarding) {
                // NEW CUSTOMER FLOW: Create everything from scratch
                handleOnboardingCheckout(session, subscriptionId);
            } else {
                // EXISTING TENANT FLOW: Update limits only
                handleExistingTenantCheckout(session, subscriptionId);
            }

        } catch (Exception e) {
            log.error("Error handling checkout completion", e);
            throw new RuntimeException("Error al procesar el pago completado: " + e.getMessage());
        }
    }

    /**
     * Handle checkout for NEW customers (onboarding flow)
     * Creates organization, tenant, config, and assigns user as TENANT_ADMIN
     */
    private void handleOnboardingCheckout(Session session, String subscriptionId) throws StripeException {
        Map<String, String> metadata = session.getMetadata();

        Long userId = Long.parseLong(metadata.get("user_id"));
        String organizationName = metadata.get("organization_name");
        String tenantName = metadata.get("tenant_name");
        String tenantDescription = metadata.get("tenant_description");
        Long appId = Long.parseLong(metadata.get("app_id"));
        Integer accountQuantity = Integer.parseInt(metadata.get("account_quantity"));
        Integer ridersQuantity = Integer.parseInt(metadata.get("riders_quantity"));

        // 1. Get user and app
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        AppEntity app = appRepository.findById(appId)
                .orElseThrow(() -> new ResourceNotFoundException("Aplicación no encontrada"));

        // 2. Create or get organization
        OrganizationEntity organization = organizationRepository
                .findByName(organizationName)
                .orElseGet(() -> {
                    OrganizationEntity newOrg = new OrganizationEntity();
                    newOrg.setName(organizationName);
                    newOrg.setIsActive(true);
                    newOrg.setCreatedAt(LocalDateTime.now());
                    return organizationRepository.save(newOrg);
                });

        // 3. Create tenant (or reuse if webhook is a retry)
        Optional<TenantEntity> existingTenant = tenantRepository
                .findByAppAndOrganizationAndName(app, organization, tenantName);

        TenantEntity tenant;
        if (existingTenant.isPresent()) {
            log.info("Tenant '{}' already exists (webhook retry detected), reusing existing tenant",
                    tenantName);
            tenant = existingTenant.get();
        } else {
            tenant = new TenantEntity();
            tenant.setApp(app);
            tenant.setOrganization(organization);
            tenant.setName(tenantName);
            tenant.setDescription(tenantDescription);
            tenant.setAccountLimit(accountQuantity);
            tenant.setIsActive(true);
            tenant.setCreatedAt(LocalDateTime.now());
            tenant = tenantRepository.save(tenant);
            log.info("Created new tenant: {}", tenantName);
        }

        // 4. Create tenant configuration based on app type (if not exists)
        if ("RiTrack".equals(app.getName())) {
            if (tenant.getRidersConfig() == null) {
                TenantRidersConfigEntity ridersConfig = new TenantRidersConfigEntity();
                ridersConfig.setTenant(tenant);
                ridersConfig.setRiderLimit(ridersQuantity);
                ridersConfig.setDeliveryZones(1); // Default to 1 zone (constraint requires > 0 or NULL)
                ridersConfig.setMaxDailyDeliveries(null);
                ridersConfig.setRealTimeTracking(true);
                ridersConfig.setSmsNotifications(false);
                tenantRidersConfigRepository.save(ridersConfig);
                log.info("Created riders configuration for tenant: {}", tenantName);
            } else {
                log.info("Riders configuration already exists for tenant: {} (webhook retry)", tenantName);
            }
        }
        // TODO: Add other app types (Warehouse, Fleet) when needed

        // 5. Assign user as TENANT_ADMIN (if not already assigned)
        Optional<UserTenantRoleEntity> existingAssignment = userTenantRoleRepository
                .findByUserAndTenant(user, tenant);

        if (existingAssignment.isEmpty()) {
            UserTenantRoleEntity userTenantRole = UserTenantRoleEntity.builder()
                    .user(user)
                    .tenant(tenant)
                    .role(Role.TENANT_ADMIN.name())
                    .build();
            userTenantRoleRepository.save(userTenantRole);
            log.info("Assigned user {} as TENANT_ADMIN for tenant: {}", user.getEmail(), tenantName);
        } else {
            log.info("User {} already assigned to tenant: {} (webhook retry)", user.getEmail(), tenantName);
        }

        // 6. Retrieve full subscription details from Stripe
        Subscription stripeSubscription = Subscription.retrieve(subscriptionId);

        // 7. Save subscription to database (if not already exists)
        Optional<StripeSubscriptionEntity> existingSubscription =
                subscriptionRepository.findByStripeSubscriptionId(subscriptionId);

        if (existingSubscription.isEmpty()) {
            saveSubscriptionFromStripe(stripeSubscription, organization.getId(), tenant.getId());
            log.info("Saved subscription {} for tenant: {}", subscriptionId, tenantName);
        } else {
            log.info("Subscription {} already exists (webhook retry)", subscriptionId);
        }

        log.info("Successfully completed onboarding for user: {}, tenant: {} ({} accounts, {} riders)",
                 user.getEmail(), tenant.getName(), accountQuantity, ridersQuantity);
    }

    /**
     * Handle checkout for EXISTING tenants (upgrade/update flow)
     * Only updates limits, doesn't create new entities
     */
    private void handleExistingTenantCheckout(Session session, String subscriptionId) throws StripeException {
        Map<String, String> metadata = session.getMetadata();

        Long organizationId = Long.parseLong(metadata.get("organization_id"));
        Long tenantId = Long.parseLong(metadata.get("tenant_id"));
        Integer accountQuantity = Integer.parseInt(metadata.get("account_quantity"));
        Integer ridersQuantity = Integer.parseInt(metadata.get("riders_quantity"));

        // Retrieve full subscription details from Stripe
        Subscription stripeSubscription = Subscription.retrieve(subscriptionId);

        // Save subscription to database
        saveSubscriptionFromStripe(stripeSubscription, organizationId, tenantId);

        // Update tenant limits based on purchased quantities
        updateTenantLimits(tenantId, accountQuantity, ridersQuantity);

        log.info("Successfully processed checkout for existing tenant: {} ({} accounts, {} riders)",
                 tenantId, accountQuantity, ridersQuantity);
    }

    /**
     * Save or update subscription from Stripe object
     */
    @Transactional
    public void saveSubscriptionFromStripe(Subscription stripeSubscription, Long organizationId, Long tenantId) {
        OrganizationEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organización no encontrada"));

        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        // Check if subscription already exists
        StripeSubscriptionEntity subscription = subscriptionRepository
                .findByStripeSubscriptionId(stripeSubscription.getId())
                .orElse(new StripeSubscriptionEntity());

        // Update fields
        subscription.setTenant(tenant);
        subscription.setOrganization(organization);
        subscription.setStripeCustomerId(stripeSubscription.getCustomer());
        subscription.setStripeSubscriptionId(stripeSubscription.getId());
        subscription.setStripePriceId(stripeSubscription.getItems().getData().get(0).getPrice().getId());
        subscription.setStripeProductId(stripeSubscription.getItems().getData().get(0).getPrice().getProduct());
        subscription.setStatus(stripeSubscription.getStatus());
        subscription.setCurrentPeriodStart(toLocalDateTime(stripeSubscription.getCurrentPeriodStart()));
        subscription.setCurrentPeriodEnd(toLocalDateTime(stripeSubscription.getCurrentPeriodEnd()));
        subscription.setCancelAtPeriodEnd(stripeSubscription.getCancelAtPeriodEnd());

        if (stripeSubscription.getCanceledAt() != null) {
            subscription.setCanceledAt(toLocalDateTime(stripeSubscription.getCanceledAt()));
        }

        subscriptionRepository.save(subscription);

        log.info("Saved subscription {} for tenant {}", stripeSubscription.getId(), tenantId);
    }

    /**
     * Cancel subscription at the end of the current period
     */
    @Transactional
    public void cancelSubscription(Long tenantId) {
        try {
            StripeSubscriptionEntity subscription = subscriptionRepository.findByTenantId(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));

            // Cancel at period end (don't cancel immediately)
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(true)
                    .build();

            Subscription stripeSubscription = Subscription.retrieve(subscription.getStripeSubscriptionId());
            stripeSubscription.update(params);

            // Update local database
            subscription.setCancelAtPeriodEnd(true);
            subscriptionRepository.save(subscription);

            log.info("Subscription {} will be canceled at period end", subscription.getStripeSubscriptionId());

        } catch (StripeException e) {
            log.error("Error canceling subscription", e);
            throw new RuntimeException("Error al cancelar la suscripción: " + e.getMessage());
        }
    }

    /**
     * Reactivate a subscription that was set to cancel
     */
    @Transactional
    public void reactivateSubscription(Long tenantId) {
        try {
            StripeSubscriptionEntity subscription = subscriptionRepository.findByTenantId(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));

            // Remove cancellation
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(false)
                    .build();

            Subscription stripeSubscription = Subscription.retrieve(subscription.getStripeSubscriptionId());
            stripeSubscription.update(params);

            // Update local database
            subscription.setCancelAtPeriodEnd(false);
            subscriptionRepository.save(subscription);

            log.info("Subscription {} reactivated", subscription.getStripeSubscriptionId());

        } catch (StripeException e) {
            log.error("Error reactivating subscription", e);
            throw new RuntimeException("Error al reactivar la suscripción: " + e.getMessage());
        }
    }

    /**
     * Update subscription to a different price (upgrade/downgrade)
     */
    @Transactional
    public void updateSubscriptionPrice(Long tenantId, String newPriceId) {
        try {
            StripeSubscriptionEntity subscription = subscriptionRepository.findByTenantId(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));

            Subscription stripeSubscription = Subscription.retrieve(subscription.getStripeSubscriptionId());

            // Get the subscription item ID
            String subscriptionItemId = stripeSubscription.getItems().getData().get(0).getId();

            // Update the subscription with new price
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .addItem(
                            SubscriptionUpdateParams.Item.builder()
                                    .setId(subscriptionItemId)
                                    .setPrice(newPriceId)
                                    .build()
                    )
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE)
                    .build();

            stripeSubscription.update(params);

            // Update local database
            subscription.setStripePriceId(newPriceId);
            subscriptionRepository.save(subscription);

            log.info("Updated subscription {} to new price {}", subscription.getStripeSubscriptionId(), newPriceId);

        } catch (StripeException e) {
            log.error("Error updating subscription price", e);
            throw new RuntimeException("Error al actualizar el plan: " + e.getMessage());
        }
    }

    /**
     * Create a Stripe Billing Portal session
     * Allows customers to manage payment methods, view invoices, and update billing information
     *
     * @param tenantId Tenant ID
     * @return Billing Portal URL for customer redirect
     */
    @Transactional(readOnly = true)
    public String createBillingPortalSession(Long tenantId) {
        try {
            // Get subscription with customer ID
            StripeSubscriptionEntity subscription = subscriptionRepository.findByTenantId(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada para este tenant"));

            String customerId = subscription.getStripeCustomerId();
            if (customerId == null || customerId.isEmpty()) {
                throw new IllegalStateException("No se encontró un customer ID de Stripe para este tenant");
            }

            // Create billing portal session
            com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setReturnUrl(successUrl + "?tenantId=" + tenantId)
                    .build();

            com.stripe.model.billingportal.Session portalSession =
                com.stripe.model.billingportal.Session.create(params);

            log.info("Created billing portal session for customer: {}, tenant: {}", customerId, tenantId);

            return portalSession.getUrl();

        } catch (StripeException e) {
            log.error("Error creating billing portal session for tenant: {}", tenantId, e);
            throw new RuntimeException("Error al crear la sesión del portal de facturación: " + e.getMessage());
        }
    }

    // ==================== WEBHOOK HANDLERS ====================

    /**
     * Handle subscription updated webhook
     */
    @Transactional
    public void handleSubscriptionUpdated(Subscription stripeSubscription) {
        try {
            // Find subscription - if it doesn't exist yet, just log and return
            // (this can happen when subscription.updated arrives before checkout.completed)
            Optional<StripeSubscriptionEntity> subscriptionOpt = subscriptionRepository
                    .findByStripeSubscriptionId(stripeSubscription.getId());

            if (subscriptionOpt.isEmpty()) {
                log.warn("Subscription {} not found in database yet - will be created by checkout.completed event",
                        stripeSubscription.getId());
                return;
            }

            StripeSubscriptionEntity subscription = subscriptionOpt.get();

            // Update status and other fields
            subscription.setStatus(stripeSubscription.getStatus());

            // Only update period dates if they're not null
            if (stripeSubscription.getCurrentPeriodStart() != null) {
                subscription.setCurrentPeriodStart(toLocalDateTime(stripeSubscription.getCurrentPeriodStart()));
            }
            if (stripeSubscription.getCurrentPeriodEnd() != null) {
                subscription.setCurrentPeriodEnd(toLocalDateTime(stripeSubscription.getCurrentPeriodEnd()));
            }

            subscription.setCancelAtPeriodEnd(stripeSubscription.getCancelAtPeriodEnd());

            if (stripeSubscription.getCanceledAt() != null) {
                subscription.setCanceledAt(toLocalDateTime(stripeSubscription.getCanceledAt()));
            }

            subscriptionRepository.save(subscription);

            log.info("Updated subscription {}", stripeSubscription.getId());

        } catch (Exception e) {
            log.error("Error handling subscription update", e);
        }
    }

    /**
     * Handle subscription deleted webhook
     */
    @Transactional
    public void handleSubscriptionDeleted(Subscription stripeSubscription) {
        try {
            StripeSubscriptionEntity subscription = subscriptionRepository
                    .findByStripeSubscriptionId(stripeSubscription.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));

            subscription.setStatus("canceled");
            subscription.setCanceledAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);

            log.info("Subscription {} deleted/canceled", stripeSubscription.getId());

        } catch (Exception e) {
            log.error("Error handling subscription deletion", e);
        }
    }

    /**
     * Handle invoice payment succeeded webhook
     */
    @Transactional
    public void handleInvoicePaymentSucceeded(Invoice invoice) {
        try {
            // Retrieve full invoice with subscription expanded
            String invoiceId = invoice.getId();
            com.stripe.param.InvoiceRetrieveParams params = com.stripe.param.InvoiceRetrieveParams.builder()
                    .addExpand("subscription")
                    .build();

            Invoice fullInvoice = null;
            try {
                fullInvoice = Invoice.retrieve(invoiceId, params, null);
            } catch (Exception e) {
                log.error("Failed to retrieve invoice {}", invoiceId, e);
                return;
            }

            String subscriptionId = fullInvoice.getSubscription();
            if (subscriptionId == null) {
                log.warn("Invoice {} has no subscription", invoiceId);
                return;
            }

            StripeSubscriptionEntity subscription = subscriptionRepository
                    .findByStripeSubscriptionId(subscriptionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));

            // Check if payment already recorded
            if (paymentHistoryRepository.existsByStripeInvoiceId(fullInvoice.getId())) {
                log.info("Payment history already exists for invoice {}", fullInvoice.getId());
                return;
            }

            // Create payment history record
            StripePaymentHistoryEntity payment = new StripePaymentHistoryEntity();
            payment.setSubscription(subscription);
            payment.setStripeInvoiceId(fullInvoice.getId());
            payment.setStripePaymentIntentId(fullInvoice.getPaymentIntent());
            payment.setStripeChargeId(fullInvoice.getCharge());
            payment.setAmountCents(fullInvoice.getAmountPaid().intValue());
            payment.setCurrency(fullInvoice.getCurrency().toUpperCase());
            payment.setStatus("paid");
            payment.setInvoicePdfUrl(fullInvoice.getInvoicePdf());
            payment.setHostedInvoiceUrl(fullInvoice.getHostedInvoiceUrl());
            payment.setAttemptedAt(toLocalDateTime(fullInvoice.getCreated()));
            payment.setPaidAt(toLocalDateTime(fullInvoice.getStatusTransitions().getPaidAt()));

            paymentHistoryRepository.save(payment);

            log.info("Recorded successful payment for invoice {}", fullInvoice.getId());

        } catch (Exception e) {
            log.error("Error handling invoice payment success", e);
        }
    }

    /**
     * Handle invoice payment failed webhook
     */
    @Transactional
    public void handleInvoicePaymentFailed(Invoice invoice) {
        try {
            String subscriptionId = invoice.getSubscription();
            if (subscriptionId == null) {
                log.warn("Invoice {} has no subscription", invoice.getId());
                return;
            }

            StripeSubscriptionEntity subscription = subscriptionRepository
                    .findByStripeSubscriptionId(subscriptionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));

            // Update subscription status
            subscription.setStatus("past_due");
            subscriptionRepository.save(subscription);

            // TODO: Send email notification to customer

            log.warn("Payment failed for subscription {}, invoice {}", subscriptionId, invoice.getId());

        } catch (Exception e) {
            log.error("Error handling invoice payment failure", e);
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Get subscription by tenant ID
     */
    @Transactional(readOnly = true)
    public StripeSubscriptionEntity getSubscriptionByTenantId(Long tenantId) {
        return subscriptionRepository.findByTenantIdWithDetails(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada para el tenant"));
    }

    /**
     * Get payment history for a tenant
     */
    @Transactional(readOnly = true)
    public List<StripePaymentHistoryEntity> getPaymentHistory(Long tenantId) {
        StripeSubscriptionEntity subscription = getSubscriptionByTenantId(tenantId);
        return paymentHistoryRepository.findBySubscriptionId(subscription.getId());
    }

    /**
     * Update subscription quantities (accounts and riders)
     * Allows customers to upgrade/downgrade their plan
     *
     * VALIDACIÓN DE DOWNGRADE:
     * - Si el usuario intenta reducir riders a menos de los que tiene actualmente en RiTrack, se bloquea
     */
    @Transactional
    public void updateSubscriptionQuantities(Long tenantId, Integer newAccountQuantity, Integer newRidersQuantity) {
        try {
            StripeSubscriptionEntity subscription = subscriptionRepository.findByTenantId(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));

            // VALIDACIÓN: Prevenir downgrade con riders activos
            TenantEntity tenant = subscription.getTenant();
            if (tenant.getRidersConfig() != null) {
                Integer currentLimit = tenant.getRidersConfig().getRiderLimit();

                // Si está intentando reducir riders, validar con RiTrack
                if (currentLimit != null && newRidersQuantity < currentLimit) {
                    log.info("Tenant {}: Intentando reducir riders de {} a {}, validando con RiTrack...",
                            tenantId, currentLimit, newRidersQuantity);

                    // Consultar a RiTrack cuántos riders tiene actualmente
                    Integer actualRiderCount = getRealRiderCountFromRiTrack(tenant);

                    if (actualRiderCount != null && newRidersQuantity < actualRiderCount) {
                        log.error("Tenant {}: DOWNGRADE BLOQUEADO - Tiene {} riders activos pero intenta reducir a {}",
                                tenantId, actualRiderCount, newRidersQuantity);

                        throw new IllegalStateException(
                                String.format(
                                        "No puedes reducir el límite de riders a %d porque actualmente tienes %d riders activos.",
                                        newRidersQuantity, actualRiderCount
                                )
                        );
                    }
                }
            }

            Subscription stripeSubscription = Subscription.retrieve(subscription.getStripeSubscriptionId());

            // Find subscription items for accounts and riders
            String accountsItemId = null;
            String ridersItemId = null;

            for (com.stripe.model.SubscriptionItem item : stripeSubscription.getItems().getData()) {
                String priceId = item.getPrice().getId();
                if (priceId.equals(accountsPriceId)) {
                    accountsItemId = item.getId();
                } else if (priceId.equals(ridersPriceId)) {
                    ridersItemId = item.getId();
                }
            }

            // Update subscription with new quantities
            SubscriptionUpdateParams.Builder paramsBuilder = SubscriptionUpdateParams.builder()
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE);

            if (accountsItemId != null) {
                paramsBuilder.addItem(
                        SubscriptionUpdateParams.Item.builder()
                                .setId(accountsItemId)
                                .setQuantity(newAccountQuantity.longValue())
                                .build()
                );
            }

            if (ridersItemId != null) {
                paramsBuilder.addItem(
                        SubscriptionUpdateParams.Item.builder()
                                .setId(ridersItemId)
                                .setQuantity(newRidersQuantity.longValue())
                                .build()
                );
            }

            stripeSubscription.update(paramsBuilder.build());

            // Update tenant limits
            updateTenantLimits(tenantId, newAccountQuantity, newRidersQuantity);

            log.info("Updated subscription quantities for tenant {}: {} accounts, {} riders",
                     tenantId, newAccountQuantity, newRidersQuantity);

        } catch (StripeException e) {
            log.error("Error updating subscription quantities", e);
            throw new RuntimeException("Error al actualizar las cantidades: " + e.getMessage());
        }
    }

    /**
     * Update tenant limits based on purchased quantities
     * Updates both account_limit and rider_limit
     */
    @Transactional
    public void updateTenantLimits(Long tenantId, Integer accountQuantity, Integer ridersQuantity) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        // Update account_limit
        tenant.setAccountLimit(accountQuantity);
        tenantRepository.save(tenant);

        // Update rider_limit in tenant_riders_config
        TenantRidersConfigEntity ridersConfig = tenant.getRidersConfig();
        if (ridersConfig == null) {
            // Create if doesn't exist
            ridersConfig = new TenantRidersConfigEntity();
            ridersConfig.setTenant(tenant);
            tenant.setRidersConfig(ridersConfig);
        }

        ridersConfig.setRiderLimit(ridersQuantity);
        tenantRidersConfigRepository.save(ridersConfig);

        log.info("Updated tenant {} limits: {} accounts, {} riders", tenantId, accountQuantity, ridersQuantity);

        // Publish event - RiTrack will be notified AFTER transaction commits
        log.info("Publishing TenantLimitsUpdatedEvent for tenant {}", tenantId);
        eventPublisher.publishEvent(new TenantLimitsUpdatedEvent(tenantId, accountQuantity, ridersQuantity));
    }

    /**
     * Get the real rider count from RiTrack for a tenant.
     * Used to validate subscription downgrades.
     *
     * @param tenant Tenant entity with hargosTenantId
     * @return Actual rider count from RiTrack, or null if RiTrack is unreachable
     */
    private Integer getRealRiderCountFromRiTrack(TenantEntity tenant) {
        if (tenant.getId() == null) {
            log.warn("Cannot query RiTrack: tenant ID is null");
            return null;
        }

        try {
            String url = ritrackBaseUrl + "/api/ritrack-public/tenant/" + tenant.getId() + "/rider-count";
            log.debug("Querying RiTrack for rider count: {}", url);

            ResponseEntity<RiderCountResponse> response = restTemplate.getForEntity(url, RiderCountResponse.class);

            if (response.getBody() != null) {
                Integer count = response.getBody().riderCount();
                log.info("RiTrack reports {} riders for tenant {}", count, tenant.getId());
                return count;
            }

            log.warn("RiTrack returned null response for tenant {}", tenant.getId());
            return null;

        } catch (Exception e) {
            log.error("Failed to query RiTrack for tenant {}: {}", tenant.getId(), e.getMessage());
            // Return null to allow downgrade if RiTrack is unreachable (fail-open strategy)
            // Alternative: throw exception to fail-closed (more strict)
            return null;
        }
    }

    /**
     * Convert Unix timestamp to LocalDateTime
     */
    private LocalDateTime toLocalDateTime(Long unixTimestamp) {
        if (unixTimestamp == null) {
            return null;
        }
        return LocalDateTime.ofInstant(
                Instant.ofEpochSecond(unixTimestamp),
                ZoneId.systemDefault()
        );
    }

    // ==================== DTOs for RiTrack Communication ====================

    /**
     * Response from RiTrack public API for rider count
     */
    private record RiderCountResponse(Integer riderCount, String message) {
    }
}
