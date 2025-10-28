package es.hargos.auth.service;

import com.stripe.model.checkout.Session;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import com.stripe.model.Price;
import es.hargos.auth.entity.*;
import es.hargos.auth.exception.ResourceNotFoundException;
import es.hargos.auth.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StripeService
 * Tests all webhook handlers and subscription management logic
 */
@ExtendWith(MockitoExtension.class)
class StripeServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private StripeSubscriptionRepository subscriptionRepository;

    @Mock
    private StripePaymentHistoryRepository paymentHistoryRepository;

    @Mock
    private TenantRidersConfigRepository tenantRidersConfigRepository;

    @InjectMocks
    private StripeService stripeService;

    private TenantEntity testTenant;
    private OrganizationEntity testOrganization;
    private StripeSubscriptionEntity testSubscription;
    private TenantRidersConfigEntity testRidersConfig;

    @BeforeEach
    void setUp() {
        // Setup test organization
        testOrganization = new OrganizationEntity();
        testOrganization.setId(1L);
        testOrganization.setName("Test Organization");

        // Setup test tenant
        testTenant = new TenantEntity();
        testTenant.setId(1L);
        testTenant.setName("Test Tenant");
        testTenant.setOrganization(testOrganization);
        testTenant.setAccountLimit(null);

        // Setup test riders config
        testRidersConfig = new TenantRidersConfigEntity();
        testRidersConfig.setId(1L);
        testRidersConfig.setTenant(testTenant);
        testRidersConfig.setRiderLimit(null);
        testTenant.setRidersConfig(testRidersConfig);

        // Setup test subscription
        testSubscription = new StripeSubscriptionEntity();
        testSubscription.setId(1L);
        testSubscription.setTenant(testTenant);
        testSubscription.setOrganization(testOrganization);
        testSubscription.setStripeSubscriptionId("sub_test123");
        testSubscription.setStripeCustomerId("cus_test123");
        testSubscription.setStripePriceId("price_test123");
        testSubscription.setStatus("active");
    }

    @Test
    void testUpdateTenantLimits_Success() {
        // Arrange
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(testTenant));
        when(tenantRepository.save(any(TenantEntity.class))).thenReturn(testTenant);
        when(tenantRidersConfigRepository.save(any(TenantRidersConfigEntity.class)))
                .thenReturn(testRidersConfig);

        // Act
        stripeService.updateTenantLimits(1L, 5, 150);

        // Assert
        assertEquals(5, testTenant.getAccountLimit());
        assertEquals(150, testRidersConfig.getRiderLimit());
        verify(tenantRepository, times(1)).save(testTenant);
        verify(tenantRidersConfigRepository, times(1)).save(testRidersConfig);
    }

    @Test
    void testUpdateTenantLimits_TenantNotFound() {
        // Arrange
        when(tenantRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            stripeService.updateTenantLimits(999L, 5, 150);
        });
    }

    @Test
    void testUpdateTenantLimits_CreatesRidersConfigIfNotExists() {
        // Arrange
        testTenant.setRidersConfig(null);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(testTenant));
        when(tenantRepository.save(any(TenantEntity.class))).thenReturn(testTenant);
        when(tenantRidersConfigRepository.save(any(TenantRidersConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        stripeService.updateTenantLimits(1L, 5, 150);

        // Assert
        assertNotNull(testTenant.getRidersConfig());
        assertEquals(5, testTenant.getAccountLimit());
        assertEquals(150, testTenant.getRidersConfig().getRiderLimit());
        verify(tenantRidersConfigRepository, times(1)).save(any(TenantRidersConfigEntity.class));
    }

    @Test
    void testSaveSubscriptionFromStripe_Success() {
        // Arrange
        Subscription mockStripeSubscription = mock(Subscription.class);
        SubscriptionItemCollection mockItemCollection = mock(SubscriptionItemCollection.class);
        SubscriptionItem mockItem = mock(SubscriptionItem.class);
        Price mockPrice = mock(Price.class);

        when(mockStripeSubscription.getId()).thenReturn("sub_test123");
        when(mockStripeSubscription.getCustomer()).thenReturn("cus_test123");
        when(mockStripeSubscription.getStatus()).thenReturn("active");
        when(mockStripeSubscription.getCurrentPeriodStart()).thenReturn(System.currentTimeMillis() / 1000);
        when(mockStripeSubscription.getCurrentPeriodEnd()).thenReturn(System.currentTimeMillis() / 1000 + 2592000);
        when(mockStripeSubscription.getCancelAtPeriodEnd()).thenReturn(false);
        when(mockStripeSubscription.getCanceledAt()).thenReturn(null);
        when(mockStripeSubscription.getItems()).thenReturn(mockItemCollection);
        when(mockItemCollection.getData()).thenReturn(java.util.List.of(mockItem));
        when(mockItem.getPrice()).thenReturn(mockPrice);
        when(mockPrice.getId()).thenReturn("price_test123");
        when(mockPrice.getProduct()).thenReturn("prod_test123");

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(testTenant));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrganization));
        when(subscriptionRepository.findByStripeSubscriptionId("sub_test123"))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(StripeSubscriptionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        stripeService.saveSubscriptionFromStripe(mockStripeSubscription, 1L, 1L);

        // Assert
        verify(subscriptionRepository, times(1)).save(any(StripeSubscriptionEntity.class));
        verify(tenantRepository, times(1)).findById(1L);
        verify(organizationRepository, times(1)).findById(1L);
    }

    @Test
    void testHandleSubscriptionUpdated_Success() {
        // Arrange
        Subscription mockSubscription = mock(Subscription.class);
        when(mockSubscription.getId()).thenReturn("sub_test123");
        when(mockSubscription.getStatus()).thenReturn("active");
        when(mockSubscription.getCurrentPeriodStart()).thenReturn(System.currentTimeMillis() / 1000);
        when(mockSubscription.getCurrentPeriodEnd()).thenReturn(System.currentTimeMillis() / 1000 + 2592000);
        when(mockSubscription.getCancelAtPeriodEnd()).thenReturn(false);
        when(mockSubscription.getCanceledAt()).thenReturn(null);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_test123"))
                .thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.save(any(StripeSubscriptionEntity.class)))
                .thenReturn(testSubscription);

        // Act
        stripeService.handleSubscriptionUpdated(mockSubscription);

        // Assert
        verify(subscriptionRepository, times(1)).save(testSubscription);
        assertEquals("active", testSubscription.getStatus());
        assertFalse(testSubscription.getCancelAtPeriodEnd());
    }

    @Test
    void testHandleSubscriptionUpdated_WithNullPeriodDates() {
        // Arrange
        Subscription mockSubscription = mock(Subscription.class);
        when(mockSubscription.getId()).thenReturn("sub_test123");
        when(mockSubscription.getStatus()).thenReturn("active");
        when(mockSubscription.getCurrentPeriodStart()).thenReturn(null);
        when(mockSubscription.getCurrentPeriodEnd()).thenReturn(null);
        when(mockSubscription.getCancelAtPeriodEnd()).thenReturn(false);
        when(mockSubscription.getCanceledAt()).thenReturn(null);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_test123"))
                .thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.save(any(StripeSubscriptionEntity.class)))
                .thenReturn(testSubscription);

        // Act
        stripeService.handleSubscriptionUpdated(mockSubscription);

        // Assert
        verify(subscriptionRepository, times(1)).save(testSubscription);
        assertNull(testSubscription.getCurrentPeriodStart());
        assertNull(testSubscription.getCurrentPeriodEnd());
    }

    @Test
    void testHandleSubscriptionUpdated_SubscriptionNotFound() {
        // Arrange
        Subscription mockSubscription = mock(Subscription.class);
        when(mockSubscription.getId()).thenReturn("sub_nonexistent");

        when(subscriptionRepository.findByStripeSubscriptionId("sub_nonexistent"))
                .thenReturn(Optional.empty());

        // Act - should not throw exception
        stripeService.handleSubscriptionUpdated(mockSubscription);

        // Assert - should not try to save anything
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void testHandleSubscriptionDeleted_Success() {
        // Arrange
        Subscription mockSubscription = mock(Subscription.class);

        when(mockSubscription.getId()).thenReturn("sub_test123");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_test123"))
                .thenReturn(Optional.of(testSubscription));

        // Act
        stripeService.handleSubscriptionDeleted(mockSubscription);

        // Assert
        verify(subscriptionRepository, times(1)).save(testSubscription);
        assertEquals("canceled", testSubscription.getStatus());
        assertNotNull(testSubscription.getCanceledAt());
    }

    @Test
    void testHandleInvoicePaymentSucceeded_WithSubscription() {
        // Arrange
        Invoice mockInvoice = mock(Invoice.class);
        Invoice.StatusTransitions mockStatusTransitions = mock(Invoice.StatusTransitions.class);

        when(mockInvoice.getId()).thenReturn("in_test123");
        when(mockInvoice.getSubscription()).thenReturn("sub_test123");
        when(mockInvoice.getAmountPaid()).thenReturn(29500L);
        when(mockInvoice.getCurrency()).thenReturn("eur");
        when(mockInvoice.getPaymentIntent()).thenReturn("pi_test123");
        when(mockInvoice.getCharge()).thenReturn("ch_test123");
        when(mockInvoice.getInvoicePdf()).thenReturn("https://stripe.com/invoice.pdf");
        when(mockInvoice.getHostedInvoiceUrl()).thenReturn("https://stripe.com/invoice");
        when(mockInvoice.getCreated()).thenReturn(System.currentTimeMillis() / 1000);
        when(mockInvoice.getStatusTransitions()).thenReturn(mockStatusTransitions);
        when(mockStatusTransitions.getPaidAt()).thenReturn(System.currentTimeMillis() / 1000);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_test123"))
                .thenReturn(Optional.of(testSubscription));
        when(paymentHistoryRepository.existsByStripeInvoiceId("in_test123"))
                .thenReturn(false);
        when(paymentHistoryRepository.save(any(StripePaymentHistoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        stripeService.handleInvoicePaymentSucceeded(mockInvoice);

        // Assert
        verify(paymentHistoryRepository, times(1)).save(any(StripePaymentHistoryEntity.class));
    }

    @Test
    void testHandleInvoicePaymentSucceeded_WithoutSubscription() {
        // Arrange
        Invoice mockInvoice = mock(Invoice.class);
        when(mockInvoice.getId()).thenReturn("in_test123");
        when(mockInvoice.getSubscription()).thenReturn(null);

        // Act
        stripeService.handleInvoicePaymentSucceeded(mockInvoice);

        // Assert
        verify(paymentHistoryRepository, never()).save(any());
    }

    @Test
    void testHandleInvoicePaymentFailed_Success() {
        // Arrange
        Invoice mockInvoice = mock(Invoice.class);
        when(mockInvoice.getId()).thenReturn("in_test123");
        when(mockInvoice.getSubscription()).thenReturn("sub_test123");

        when(subscriptionRepository.findByStripeSubscriptionId("sub_test123"))
                .thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.save(any(StripeSubscriptionEntity.class)))
                .thenReturn(testSubscription);

        // Act
        stripeService.handleInvoicePaymentFailed(mockInvoice);

        // Assert
        verify(subscriptionRepository, times(1)).save(testSubscription);
        assertEquals("past_due", testSubscription.getStatus());
    }
}
