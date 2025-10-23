package es.hargos.auth.controller;

import es.hargos.auth.dto.request.AssignTenantRequest;
import es.hargos.auth.dto.request.CreateOrganizationRequest;
import es.hargos.auth.dto.request.CreateTenantRequest;
import es.hargos.auth.dto.request.CreateUserRequest;
import es.hargos.auth.dto.response.MessageResponse;
import es.hargos.auth.dto.response.OrganizationResponse;
import es.hargos.auth.dto.response.TenantResponse;
import es.hargos.auth.dto.response.UserResponse;
import es.hargos.auth.service.OrganizationService;
import es.hargos.auth.service.TenantService;
import es.hargos.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("@authz.isSuperAdmin()")
public class AdminController {

    private final UserService userService;
    private final OrganizationService organizationService;
    private final TenantService tenantService;

    // ==================== USER MANAGEMENT ====================
    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/users/{id}/tenants")
    public ResponseEntity<UserResponse> assignTenant(
            @PathVariable Long id,
            @Valid @RequestBody AssignTenantRequest request) {

        UserResponse response = userService.assignTenant(id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{id}/activate")
    public ResponseEntity<UserResponse> activateUser(@PathVariable Long id) {
        UserResponse response = userService.updateUserStatus(id, true);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{id}/deactivate")
    public ResponseEntity<UserResponse> deactivateUser(@PathVariable Long id) {
        UserResponse response = userService.updateUserStatus(id, false);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<MessageResponse> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(new MessageResponse("Usuario eliminado exitosamente"));
    }

    // ==================== ORGANIZATION MANAGEMENT ====================
    @PostMapping("/organizations")
    public ResponseEntity<OrganizationResponse> createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationResponse response = organizationService.createOrganization(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/organizations")
    public ResponseEntity<List<OrganizationResponse>> getAllOrganizations() {
        List<OrganizationResponse> organizations = organizationService.getAllOrganizations();
        return ResponseEntity.ok(organizations);
    }

    @GetMapping("/organizations/{id}")
    public ResponseEntity<OrganizationResponse> getOrganizationById(@PathVariable Long id) {
        OrganizationResponse organization = organizationService.getOrganizationById(id);
        return ResponseEntity.ok(organization);
    }

    @DeleteMapping("/organizations/{id}")
    public ResponseEntity<MessageResponse> deleteOrganization(@PathVariable Long id) {
        organizationService.deleteOrganization(id);
        return ResponseEntity.ok(new MessageResponse("Organizacion eliminada exitosamente"));
    }

    // ==================== TENANT MANAGEMENT ====================
    @PostMapping("/tenants")
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        TenantResponse response = tenantService.createTenant(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/tenants")
    public ResponseEntity<List<TenantResponse>> getAllTenants() {
        List<TenantResponse> tenants = tenantService.getAllTenants();
        return ResponseEntity.ok(tenants);
    }

    @GetMapping("/tenants/{id}")
    public ResponseEntity<TenantResponse> getTenantById(@PathVariable Long id) {
        TenantResponse tenant = tenantService.getTenantById(id);
        return ResponseEntity.ok(tenant);
    }

    @GetMapping("/organizations/{organizationId}/tenants")
    public ResponseEntity<List<TenantResponse>> getTenantsByOrganization(@PathVariable Long organizationId) {
        List<TenantResponse> tenants = tenantService.getTenantsByOrganization(organizationId);
        return ResponseEntity.ok(tenants);
    }

    @DeleteMapping("/tenants/{id}")
    public ResponseEntity<MessageResponse> deleteTenant(@PathVariable Long id) {
        tenantService.deleteTenant(id);
        return ResponseEntity.ok(new MessageResponse("Tenant eliminado exitosamente"));
    }
}
