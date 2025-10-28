package es.hargos.auth.controller;

import com.stripe.model.Event;
import es.hargos.auth.dto.request.CreateCheckoutSessionRequest;
import es.hargos.auth.dto.request.UpdateSubscriptionQuantitiesRequest;
import es.hargos.auth.entity.AppEntity;
import es.hargos.auth.entity.OrganizationEntity;
import es.hargos.auth.entity.TenantEntity;
import es.hargos.auth.entity.TenantRidersConfigEntity;
import es.hargos.auth.entity.StripeSubscriptionEntity;
import es.hargos.auth.repository.*;
import es.hargos.auth.service.StripeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.hamcrest.Matchers;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for StripeController
 * Tests the full request-response cycle with mocked Stripe API
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StripeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StripeService stripeService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantRidersConfigRepository ridersConfigRepository;

    @Autowired
    private AppRepository appRepository;

    private OrganizationEntity testOrganization;
    private TenantEntity testTenant;

    @BeforeEach
    void setUp() {
        // Create test organization
        testOrganization = new OrganizationEntity();
        testOrganization.setName("Test Org for Stripe");
        testOrganization = organizationRepository.save(testOrganization);

        // Create test app
        AppEntity testApp = new AppEntity();
        testApp.setName("Test App for Stripe");
        testApp = appRepository.save(testApp);

        // Create test tenant
        testTenant = new TenantEntity();
        testTenant.setName("Test Tenant for Stripe");
        testTenant.setOrganization(testOrganization);
        testTenant.setApp(testApp);
        testTenant.setAccountLimit(1);  // Default value for tests
        testTenant = tenantRepository.save(testTenant);

        // Create riders config
        TenantRidersConfigEntity ridersConfig = new TenantRidersConfigEntity();
        ridersConfig.setTenant(testTenant);
        ridersConfig.setRiderLimit(null);
        ridersConfigRepository.save(ridersConfig);
    }

    @Test
    void testCreateCheckoutSession_Success() throws Exception {
        // Arrange
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest();
        request.setOrganizationId(testOrganization.getId());
        request.setTenantId(testTenant.getId());
        request.setAccountQuantity(5);
        request.setRidersQuantity(150);

        when(stripeService.createCheckoutSession(
            eq(testOrganization.getId()),
            eq(testTenant.getId()),
            eq(5),
            eq(150)
        )).thenReturn("https://checkout.stripe.com/c/pay/cs_test_123");

        // Act & Assert
        mockMvc.perform(post("/api/stripe/checkout/create-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl", Matchers.startsWith("https://checkout.stripe.com")))
                .andExpect(jsonPath("$.message", Matchers.containsString("5 cuentas")))
                .andExpect(jsonPath("$.message", Matchers.containsString("150 riders")));

        verify(stripeService, times(1)).createCheckoutSession(
            testOrganization.getId(),
            testTenant.getId(),
            5,
            150
        );
    }

    @Test
    void testCreateCheckoutSession_InvalidAccountQuantity() throws Exception {
        // Arrange - account quantity below minimum
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest();
        request.setOrganizationId(testOrganization.getId());
        request.setTenantId(testTenant.getId());
        request.setAccountQuantity(0);  // Invalid: minimum is 1
        request.setRidersQuantity(150);

        // Act & Assert
        mockMvc.perform(post("/api/stripe/checkout/create-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(stripeService, never()).createCheckoutSession(any(), any(), any(), any());
    }

    @Test
    void testCreateCheckoutSession_InvalidRidersQuantity() throws Exception {
        // Arrange - riders quantity above maximum
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest();
        request.setOrganizationId(testOrganization.getId());
        request.setTenantId(testTenant.getId());
        request.setAccountQuantity(5);
        request.setRidersQuantity(1001);  // Invalid: maximum is 1000

        // Act & Assert
        mockMvc.perform(post("/api/stripe/checkout/create-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(stripeService, never()).createCheckoutSession(any(), any(), any(), any());
    }

    @Test
    void testCreateCheckoutSession_BoundaryValues() throws Exception {
        // Test minimum values
        CreateCheckoutSessionRequest minRequest = new CreateCheckoutSessionRequest();
        minRequest.setOrganizationId(testOrganization.getId());
        minRequest.setTenantId(testTenant.getId());
        minRequest.setAccountQuantity(1);   // Minimum
        minRequest.setRidersQuantity(50);   // Minimum

        when(stripeService.createCheckoutSession(any(), any(), any(), any()))
                .thenReturn("https://checkout.stripe.com/c/pay/cs_test_min");

        mockMvc.perform(post("/api/stripe/checkout/create-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(minRequest)))
                .andExpect(status().isOk());

        // Test maximum values
        CreateCheckoutSessionRequest maxRequest = new CreateCheckoutSessionRequest();
        maxRequest.setOrganizationId(testOrganization.getId());
        maxRequest.setTenantId(testTenant.getId());
        maxRequest.setAccountQuantity(10);    // Maximum
        maxRequest.setRidersQuantity(1000);  // Maximum

        when(stripeService.createCheckoutSession(any(), any(), any(), any()))
                .thenReturn("https://checkout.stripe.com/c/pay/cs_test_max");

        mockMvc.perform(post("/api/stripe/checkout/create-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(maxRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @Disabled("Webhook tests require real Stripe signature generation. Use Stripe CLI for E2E webhook testing: stripe listen --forward-to http://localhost:8081/api/stripe/webhook")
    void testWebhookEndpoint_ValidSignature() throws Exception {
        // Arrange
        String webhookPayload = "{\"id\":\"evt_test123\",\"type\":\"checkout.session.completed\"}";
        String validSignature = "t=123456789,v1=signature123";

        // Act & Assert
        mockMvc.perform(post("/api/stripe/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", validSignature)
                .content(webhookPayload))
                .andExpect(status().isOk());
    }

    @Test
    @Disabled("Webhook tests require real Stripe signature generation. Use Stripe CLI for E2E webhook testing: stripe listen --forward-to http://localhost:8081/api/stripe/webhook")
    void testWebhookEndpoint_MissingSignature() throws Exception {
        // Arrange
        String webhookPayload = "{\"id\":\"evt_test123\",\"type\":\"checkout.session.completed\"}";

        // Act & Assert
        mockMvc.perform(post("/api/stripe/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"SUPER_ADMIN"})
    void testUpdateSubscriptionQuantities_ValidRequest() throws Exception {
        // Arrange
        UpdateSubscriptionQuantitiesRequest request = new UpdateSubscriptionQuantitiesRequest();
        request.setAccountQuantity(8);
        request.setRidersQuantity(300);

        doNothing().when(stripeService).updateSubscriptionQuantities(
            eq(testTenant.getId()),
            eq(8),
            eq(300)
        );

        // Act & Assert
        mockMvc.perform(put("/api/stripe/subscription/tenant/" + testTenant.getId() + "/quantities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", Matchers.containsString("8 cuentas")))
                .andExpect(jsonPath("$.message", Matchers.containsString("300 riders")));

        verify(stripeService, times(1)).updateSubscriptionQuantities(
            testTenant.getId(),
            8,
            300
        );
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"SUPER_ADMIN"})
    void testGetSubscription_Success() throws Exception {
        // Arrange
        StripeSubscriptionEntity mockSubscription = new StripeSubscriptionEntity();
        mockSubscription.setId(1L);
        mockSubscription.setStripeSubscriptionId("sub_test123");
        mockSubscription.setStatus("active");
        mockSubscription.setTenant(testTenant);
        mockSubscription.setOrganization(testOrganization);

        when(stripeService.getSubscriptionByTenantId(testTenant.getId()))
                .thenReturn(mockSubscription);

        // Act & Assert
        mockMvc.perform(get("/api/stripe/subscription/tenant/" + testTenant.getId()))
                .andExpect(status().isOk());

        verify(stripeService, times(1)).getSubscriptionByTenantId(testTenant.getId());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"SUPER_ADMIN"})
    void testCancelSubscription_Success() throws Exception {
        // Arrange
        doNothing().when(stripeService).cancelSubscription(testTenant.getId());

        // Act & Assert
        mockMvc.perform(post("/api/stripe/subscription/tenant/" + testTenant.getId() + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", Matchers.containsString("cancelada")));

        verify(stripeService, times(1)).cancelSubscription(testTenant.getId());
    }

    @Test
    @WithMockUser(username = "testuser", authorities = {"SUPER_ADMIN"})
    void testGetPaymentHistory_Success() throws Exception {
        // Arrange
        when(stripeService.getPaymentHistory(testTenant.getId()))
                .thenReturn(java.util.Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/stripe/payments/tenant/" + testTenant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(stripeService, times(1)).getPaymentHistory(testTenant.getId());
    }
}
