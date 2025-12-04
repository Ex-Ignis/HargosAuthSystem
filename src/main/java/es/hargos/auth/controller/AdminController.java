package es.hargos.auth.controller;

import es.hargos.auth.client.RiTrackClient;
import es.hargos.auth.dto.request.*;
import es.hargos.auth.dto.response.AdminSessionResponse;
import es.hargos.auth.dto.response.MessageResponse;
import es.hargos.auth.dto.response.OrganizationResponse;
import es.hargos.auth.dto.response.TenantResponse;
import es.hargos.auth.dto.response.UserResponse;
import es.hargos.auth.service.OrganizationService;
import es.hargos.auth.service.SessionService;
import es.hargos.auth.service.TenantService;
import es.hargos.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("@authz.isSuperAdmin()")
public class AdminController {

    private final UserService userService;
    private final OrganizationService organizationService;
    private final TenantService tenantService;
    private final SessionService sessionService;
    private final RiTrackClient riTrackClient;

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

    @PutMapping("/users/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/users/{id}/tenants/{tenantId}")
    public ResponseEntity<UserResponse> removeTenantFromUser(
            @PathVariable Long id,
            @PathVariable Long tenantId) {
        UserResponse response = userService.removeTenantFromUser(id, tenantId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{id}/tenants/{tenantId}/role")
    public ResponseEntity<UserResponse> updateUserTenantRole(
            @PathVariable Long id,
            @PathVariable Long tenantId,
            @Valid @RequestBody UpdateTenantRoleRequest request) {
        UserResponse response = userService.updateUserTenantRole(id, tenantId, request);
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

    @PutMapping("/organizations/{id}")
    public ResponseEntity<OrganizationResponse> updateOrganization(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        OrganizationResponse response = organizationService.updateOrganization(id, request);
        return ResponseEntity.ok(response);
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

    @GetMapping("/tenants/{id}/users")
    public ResponseEntity<List<UserResponse>> getUsersByTenant(@PathVariable Long id) {
        List<UserResponse> users = userService.getUsersByTenant(id);
        return ResponseEntity.ok(users);
    }

    @PutMapping("/tenants/{id}")
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTenantRequest request) {
        TenantResponse response = tenantService.updateTenant(id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/tenants/{id}/riders-config")
    public ResponseEntity<TenantResponse> updateRidersConfig(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRidersConfigRequest request) {
        TenantResponse response = tenantService.updateRidersConfig(id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/tenants/{id}/warehouse-config")
    public ResponseEntity<TenantResponse> updateWarehouseConfig(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWarehouseConfigRequest request) {
        TenantResponse response = tenantService.updateWarehouseConfig(id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/tenants/{id}/fleet-config")
    public ResponseEntity<TenantResponse> updateFleetConfig(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFleetConfigRequest request) {
        TenantResponse response = tenantService.updateFleetConfig(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/tenants/{id}")
    public ResponseEntity<MessageResponse> deleteTenant(@PathVariable Long id) {
        tenantService.deleteTenant(id);
        return ResponseEntity.ok(new MessageResponse("Tenant eliminado exitosamente"));
    }

    /**
     * Actualiza configuración de app externa (ej: RiTrack) para un tenant.
     * Solo para tenants de tipo RiTrack.
     */
    @PutMapping("/tenants/{id}/app-config")
    public ResponseEntity<?> updateTenantAppConfig(
            @PathVariable Long id,
            @RequestBody Map<String, Object> appConfig) {

        // Verificar que el tenant existe
        TenantResponse tenant = tenantService.getTenantById(id);

        // Verificar que es un tenant de RiTrack
        if (!"RiTrack".equals(tenant.getAppName())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Solo se puede actualizar app config para tenants de RiTrack"));
        }

        // Enviar actualización a RiTrack
        boolean success = riTrackClient.updateTenantSettings(id, appConfig);

        if (success) {
            return ResponseEntity.ok(new MessageResponse("Configuración actualizada exitosamente"));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error actualizando configuración en RiTrack"));
        }
    }

    // ==================== RIDER LIMIT WARNINGS ====================

    /**
     * Obtiene todos los warnings de límite de riders de un tenant.
     */
    @GetMapping("/tenants/{id}/warnings")
    public ResponseEntity<?> getTenantWarnings(@PathVariable Long id) {
        // Verificar que el tenant existe y es de RiTrack
        TenantResponse tenant = tenantService.getTenantById(id);
        if (!"RiTrack".equals(tenant.getAppName())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Solo tenants de RiTrack tienen warnings"));
        }

        List<Map<String, Object>> warnings = riTrackClient.getTenantWarnings(id);
        if (warnings != null) {
            return ResponseEntity.ok(Map.of("warnings", warnings));
        } else {
            return ResponseEntity.ok(Map.of("warnings", List.of()));
        }
    }

    /**
     * Crea un nuevo warning para un tenant.
     */
    @PostMapping("/tenants/{id}/warnings")
    public ResponseEntity<?> createTenantWarning(
            @PathVariable Long id,
            @RequestBody Map<String, Object> warningData) {

        TenantResponse tenant = tenantService.getTenantById(id);
        if (!"RiTrack".equals(tenant.getAppName())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Solo tenants de RiTrack pueden tener warnings"));
        }

        Map<String, Object> created = riTrackClient.createWarning(id, warningData);
        if (created != null) {
            return ResponseEntity.ok(created);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error creando warning en RiTrack"));
        }
    }

    /**
     * Actualiza un warning existente.
     */
    @PutMapping("/tenants/{id}/warnings/{warningId}")
    public ResponseEntity<?> updateTenantWarning(
            @PathVariable Long id,
            @PathVariable Long warningId,
            @RequestBody Map<String, Object> warningData) {

        TenantResponse tenant = tenantService.getTenantById(id);
        if (!"RiTrack".equals(tenant.getAppName())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Solo tenants de RiTrack pueden tener warnings"));
        }

        Map<String, Object> updated = riTrackClient.updateWarning(id, warningId, warningData);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error actualizando warning en RiTrack"));
        }
    }

    /**
     * Elimina un warning.
     */
    @DeleteMapping("/tenants/{id}/warnings/{warningId}")
    public ResponseEntity<?> deleteTenantWarning(
            @PathVariable Long id,
            @PathVariable Long warningId) {

        TenantResponse tenant = tenantService.getTenantById(id);
        if (!"RiTrack".equals(tenant.getAppName())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Solo tenants de RiTrack pueden tener warnings"));
        }

        boolean deleted = riTrackClient.deleteWarning(id, warningId);
        if (deleted) {
            return ResponseEntity.ok(new MessageResponse("Warning eliminado exitosamente"));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Error eliminando warning en RiTrack"));
        }
    }

    // ==================== SESSION MANAGEMENT ====================

    /**
     * Obtiene todas las sesiones activas del sistema.
     * Sesiones activas = actividad en los ultimos 30 minutos.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<AdminSessionResponse>> getAllActiveSessions() {
        List<AdminSessionResponse> sessions = sessionService.getAllActiveSessions();
        return ResponseEntity.ok(sessions);
    }

    /**
     * Obtiene todas las sesiones del sistema (incluidas las inactivas pero no revocadas).
     */
    @GetMapping("/sessions/all")
    public ResponseEntity<List<AdminSessionResponse>> getAllSessions() {
        List<AdminSessionResponse> sessions = sessionService.getAllSessions();
        return ResponseEntity.ok(sessions);
    }

    /**
     * Obtiene estadisticas de sesiones.
     */
    @GetMapping("/sessions/stats")
    public ResponseEntity<Map<String, Object>> getSessionStats() {
        Map<String, Object> stats = sessionService.getSessionStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Revoca (cierra) una sesion especifica.
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<MessageResponse> revokeSession(@PathVariable Long sessionId) {
        sessionService.adminRevokeSession(sessionId);
        return ResponseEntity.ok(new MessageResponse("Sesion cerrada exitosamente"));
    }

    /**
     * Revoca todas las sesiones de un usuario especifico.
     */
    @DeleteMapping("/sessions/user/{userId}")
    public ResponseEntity<MessageResponse> revokeAllUserSessions(@PathVariable Long userId) {
        int count = sessionService.adminRevokeAllUserSessions(userId);
        return ResponseEntity.ok(new MessageResponse("Se cerraron " + count + " sesiones del usuario"));
    }
}