package es.hargos.auth.controller;

import es.hargos.auth.dto.request.AssignTenantRequest;
import es.hargos.auth.dto.request.CreateAccessCodeRequest;
import es.hargos.auth.dto.request.CreateInvitationRequest;
import es.hargos.auth.dto.request.CreateUserRequest;
import es.hargos.auth.dto.response.*;
import es.hargos.auth.service.AccessCodeService;
import es.hargos.auth.service.InvitationService;
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
import org.springframework.transaction.annotation.Transactional;
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
    private final InvitationService invitationService;
    private final AccessCodeService accessCodeService;

    // ==================== USER MANAGEMENT ====================

    @PostMapping("/users")
    @Transactional
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
    @Transactional(readOnly = true)
    public ResponseEntity<List<UserResponse>> getMyManagedUsers(Authentication authentication) {
        UserEntity currentUser = getUserFromAuthentication(authentication);
        List<UserResponse> users = userService.getUsersByTenantAdmin(currentUser);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{id}")
    @Transactional(readOnly = true)
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
    @Transactional
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


    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<MessageResponse> removeUserFromMyTenants(
            @PathVariable Long id,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);
        validateUserAccessByTenantAdmin(currentUser, id);

        // Obtener los tenants que este admin gestiona
        List<UserTenantRoleEntity> adminTenants = userTenantRoleRepository
                .findByUserAndRole(currentUser, "TENANT_ADMIN");

        // Obtener el usuario a eliminar
        UserEntity targetUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Eliminar al usuario solo de los tenants que este admin gestiona
        List<UserTenantRoleEntity> targetUserTenants = userTenantRoleRepository.findByUser(targetUser);

        int tenantsRemoved = 0;
        for (UserTenantRoleEntity adminTenant : adminTenants) {
            Long managedTenantId = adminTenant.getTenant().getId();

            // Verificar si el usuario pertenece a este tenant
            boolean belongsToTenant = targetUserTenants.stream()
                    .anyMatch(utr -> utr.getTenant().getId().equals(managedTenantId));

            if (belongsToTenant) {
                userService.removeUserFromTenant(id, managedTenantId);
                tenantsRemoved++;
            }
        }

        if (tenantsRemoved == 0) {
            throw new ResourceNotFoundException("El usuario no pertenece a ninguno de tus tenants");
        }

        return ResponseEntity.ok(new MessageResponse(
                "Usuario eliminado de " + tenantsRemoved + " tenant(s) exitosamente"));
    }

    // ==================== TENANT INFORMATION ====================
    @GetMapping("/tenants")
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public ResponseEntity<TenantResponse> getTenantById(
            @PathVariable Long id,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);
        validateTenantAdminAccess(currentUser, List.of(id));

        TenantResponse tenant = tenantService.getTenantById(id);
        return ResponseEntity.ok(tenant);
    }

    @GetMapping("/tenants/{id}/users")
    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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

    // ==================== INVITATIONS MANAGEMENT ====================

    /**
     * Crear invitación por email para unirse a un tenant
     */
    @PostMapping("/invitations")
    @Transactional
    public ResponseEntity<InvitationResponse> createInvitation(
            @Valid @RequestBody CreateInvitationRequest request,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);
        validateTenantAdminAccess(currentUser, List.of(request.getTenantId()));

        InvitationResponse response = invitationService.createInvitation(request, currentUser.getId());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Listar invitaciones de un tenant
     */
    @GetMapping("/tenants/{tenantId}/invitations")
    @Transactional(readOnly = true)
    public ResponseEntity<List<InvitationResponse>> getInvitationsByTenant(
            @PathVariable Long tenantId,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);
        validateTenantAdminAccess(currentUser, List.of(tenantId));

        List<InvitationResponse> invitations = invitationService.getInvitationsByTenant(tenantId);
        return ResponseEntity.ok(invitations);
    }

    /**
     * Listar invitaciones pendientes de un tenant
     */
    @GetMapping("/tenants/{tenantId}/invitations/pending")
    @Transactional(readOnly = true)
    public ResponseEntity<List<InvitationResponse>> getPendingInvitationsByTenant(
            @PathVariable Long tenantId,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);
        validateTenantAdminAccess(currentUser, List.of(tenantId));

        List<InvitationResponse> invitations = invitationService.getPendingInvitationsByTenant(tenantId);
        return ResponseEntity.ok(invitations);
    }

    /**
     * Eliminar invitación
     */
    @DeleteMapping("/invitations/{invitationId}")
    @Transactional
    public ResponseEntity<MessageResponse> deleteInvitation(
            @PathVariable Long invitationId,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);

        // Validar que el admin gestiona el tenant de la invitación
        Long invitationTenantId = invitationService.getTenantIdByInvitationId(invitationId);
        validateTenantAdminAccess(currentUser, List.of(invitationTenantId));

        invitationService.deleteInvitation(invitationId);
        return ResponseEntity.ok(new MessageResponse("Invitación eliminada exitosamente"));
    }

    // ==================== ACCESS CODES MANAGEMENT ====================

    /**
     * Generar código de acceso para un tenant
     */
    @PostMapping("/access-codes")
    @Transactional
    public ResponseEntity<AccessCodeResponse> createAccessCode(
            @Valid @RequestBody CreateAccessCodeRequest request,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);
        validateTenantAdminAccess(currentUser, List.of(request.getTenantId()));

        AccessCodeResponse response = accessCodeService.createAccessCode(request, currentUser.getId());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Listar códigos de acceso de un tenant
     */
    @GetMapping("/tenants/{tenantId}/access-codes")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AccessCodeResponse>> getAccessCodesByTenant(
            @PathVariable Long tenantId,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);
        validateTenantAdminAccess(currentUser, List.of(tenantId));

        List<AccessCodeResponse> accessCodes = accessCodeService.getAccessCodesByTenant(tenantId);
        return ResponseEntity.ok(accessCodes);
    }

    /**
     * Listar códigos de acceso activos de un tenant
     */
    @GetMapping("/tenants/{tenantId}/access-codes/active")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AccessCodeResponse>> getActiveAccessCodesByTenant(
            @PathVariable Long tenantId,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);
        validateTenantAdminAccess(currentUser, List.of(tenantId));

        List<AccessCodeResponse> accessCodes = accessCodeService.getActiveAccessCodesByTenant(tenantId);
        return ResponseEntity.ok(accessCodes);
    }

    /**
     * Desactivar código de acceso
     */
    @PutMapping("/access-codes/{accessCodeId}/deactivate")
    @Transactional
    public ResponseEntity<MessageResponse> deactivateAccessCode(
            @PathVariable Long accessCodeId,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);

        // Validar que el admin gestiona el tenant del código
        Long accessCodeTenantId = accessCodeService.getTenantIdByAccessCodeId(accessCodeId);
        validateTenantAdminAccess(currentUser, List.of(accessCodeTenantId));

        accessCodeService.deactivateAccessCode(accessCodeId);
        return ResponseEntity.ok(new MessageResponse("Código de acceso desactivado exitosamente"));
    }

    /**
     * Eliminar código de acceso
     */
    @DeleteMapping("/access-codes/{accessCodeId}")
    @Transactional
    public ResponseEntity<MessageResponse> deleteAccessCode(
            @PathVariable Long accessCodeId,
            Authentication authentication) {

        UserEntity currentUser = getUserFromAuthentication(authentication);

        // Validar que el admin gestiona el tenant del código
        Long accessCodeTenantId = accessCodeService.getTenantIdByAccessCodeId(accessCodeId);
        validateTenantAdminAccess(currentUser, List.of(accessCodeTenantId));

        accessCodeService.deleteAccessCode(accessCodeId);
        return ResponseEntity.ok(new MessageResponse("Código de acceso eliminado exitosamente"));
    }
}
