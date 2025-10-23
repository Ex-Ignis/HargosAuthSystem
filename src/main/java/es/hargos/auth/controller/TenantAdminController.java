package es.hargos.auth.controller;

import es.hargos.auth.dto.request.AssignTenantRequest;
import es.hargos.auth.dto.request.CreateUserRequest;
import es.hargos.auth.dto.response.MessageResponse;
import es.hargos.auth.dto.response.TenantResponse;
import es.hargos.auth.dto.response.UserResponse;
import es.hargos.auth.entity.UserEntity;
import es.hargos.auth.entity.UserTenantRoleEntity;
import es.hargos.auth.exception.ForbiddenException;
import es.hargos.auth.exception.ResourceNotFoundException;
import es.hargos.auth.repository.UserRepository;
import es.hargos.auth.repository.UserTenantRoleRepository;
import es.hargos.auth.service.TenantService;
import es.hargos.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tenant-admin")
@RequiredArgsConstructor
@PreAuthorize("@authz.isTenantAdmin()")
public class TenantAdminController {

    private final UserService userService;
    private final TenantService tenantService;
    private final UserRepository userRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;

    // ==================== USER MANAGEMENT ====================
    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);

        // Validate that the tenant admin can only create users for tenants they manage
        validateTenantAdminAccess(currentUser, request.getTenantRoles().stream()
                .map(CreateUserRequest.TenantRoleAssignment::getTenantId)
                .collect(Collectors.toList()));

        UserResponse response = userService.createUser(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getMyManagedUsers(Authentication authentication) {
        UserEntity currentUser = getUserFromAuthentication(authentication);
        List<UserResponse> users = userService.getUsersByTenantAdmin(currentUser);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable Long id,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);
        UserResponse user = userService.getUserById(id);

        // Validate that the user belongs to a tenant managed by this admin
        validateUserAccessByTenantAdmin(currentUser, id);

        return ResponseEntity.ok(user);
    }

    @PostMapping("/users/{id}/tenants")
    public ResponseEntity<UserResponse> assignTenant(
            @PathVariable Long id,
            @Valid @RequestBody AssignTenantRequest request,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);

        // Validate that the tenant admin manages this tenant
        validateTenantAdminAccess(currentUser, List.of(request.getTenantId()));

        UserResponse response = userService.assignTenant(id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{id}/activate")
    public ResponseEntity<UserResponse> activateUser(
            @PathVariable Long id,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);
        validateUserAccessByTenantAdmin(currentUser, id);

        UserResponse response = userService.updateUserStatus(id, true);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{id}/deactivate")
    public ResponseEntity<UserResponse> deactivateUser(
            @PathVariable Long id,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);
        validateUserAccessByTenantAdmin(currentUser, id);

        UserResponse response = userService.updateUserStatus(id, false);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<MessageResponse> deleteUser(
            @PathVariable Long id,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);
        validateUserAccessByTenantAdmin(currentUser, id);

        userService.deleteUser(id);
        return ResponseEntity.ok(new MessageResponse("Usuario eliminado exitosamente"));
    }

    // ==================== TENANT INFORMATION ====================
    @GetMapping("/tenants")
    public ResponseEntity<List<TenantResponse>> getMyManagedTenants(Authentication authentication) {
        UserEntity currentUser = getUserFromAuthentication(authentication);

        List<UserTenantRoleEntity> adminTenants = userTenantRoleRepository
                .findByUserAndRole(currentUser, "TENANT_ADMIN");

        List<TenantResponse> tenants = adminTenants.stream()
                .map(utr -> tenantService.getTenantById(utr.getTenant().getId()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(tenants);
    }

    @GetMapping("/tenants/{id}")
    public ResponseEntity<TenantResponse> getTenantById(
            @PathVariable Long id,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);
        validateTenantAdminAccess(currentUser, List.of(id));

        TenantResponse tenant = tenantService.getTenantById(id);
        return ResponseEntity.ok(tenant);
    }

    @GetMapping("/tenants/{id}/users")
    public ResponseEntity<List<UserResponse>> getUsersByTenant(
            @PathVariable Long id,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);
        validateTenantAdminAccess(currentUser, List.of(id));

        List<UserResponse> users = userService.getUsersByTenant(id);
        return ResponseEntity.ok(users);
    }

    // ==================== HELPER METHODS ====================
    private UserEntity getUserFromAuthentication(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private void validateTenantAdminAccess(UserEntity adminUser, List<Long> tenantIds) {
        List<UserTenantRoleEntity> adminTenants = userTenantRoleRepository
                .findByUserAndRole(adminUser, "TENANT_ADMIN");

        List<Long> managedTenantIds = adminTenants.stream()
                .map(utr -> utr.getTenant().getId())
                .collect(Collectors.toList());

        for (Long tenantId : tenantIds) {
            if (!managedTenantIds.contains(tenantId)) {
                throw new ForbiddenException("No tienes permiso para gestionar este tenant");
            }
        }
    }

    private void validateUserAccessByTenantAdmin(UserEntity adminUser, Long userId) {
        List<UserTenantRoleEntity> adminTenants = userTenantRoleRepository
                .findByUserAndRole(adminUser, "TENANT_ADMIN");

        List<Long> managedTenantIds = adminTenants.stream()
                .map(utr -> utr.getTenant().getId())
                .collect(Collectors.toList());

        UserEntity targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        List<UserTenantRoleEntity> targetUserTenants = userTenantRoleRepository.findByUser(targetUser);

        boolean hasAccess = targetUserTenants.stream()
                .anyMatch(utr -> managedTenantIds.contains(utr.getTenant().getId()));

        if (!hasAccess) {
            throw new ForbiddenException("No tienes permiso para gestionar este usuario");
        }
    }
}
