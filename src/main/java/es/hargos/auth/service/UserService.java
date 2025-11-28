package es.hargos.auth.service;

import es.hargos.auth.dto.request.AssignTenantRequest;
import es.hargos.auth.dto.request.CreateUserRequest;
import es.hargos.auth.dto.request.UpdateUserRequest;
import es.hargos.auth.dto.request.UpdateTenantRoleRequest;
import es.hargos.auth.dto.response.TenantRoleResponse;
import es.hargos.auth.dto.response.UserResponse;
import es.hargos.auth.entity.TenantEntity;
import es.hargos.auth.entity.UserEntity;
import es.hargos.auth.entity.UserTenantRoleEntity;
import es.hargos.auth.exception.DuplicateResourceException;
import es.hargos.auth.exception.ResourceNotFoundException;
import es.hargos.auth.repository.TenantRepository;
import es.hargos.auth.repository.UserRepository;
import es.hargos.auth.repository.UserTenantRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // Check if user already exists
        UserEntity user;
        boolean isNewUser = false;

        if (userRepository.existsByEmail(request.getEmail())) {
            user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        } else {
            user = new UserEntity();
            user.setEmail(request.getEmail());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setFullName(request.getFullName());
            user.setIsActive(true);
            user.setEmailVerified(false);
            user = userRepository.save(user);
            isNewUser = true;
        }

        // Validate and assign tenants
        List<UserTenantRoleEntity> userTenantRoles = new java.util.ArrayList<>();

        for (CreateUserRequest.TenantRoleAssignment assignment : request.getTenantRoles()) {
            TenantEntity tenant = tenantRepository.findById(assignment.getTenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado con ID: " + assignment.getTenantId()));

            // Check if user is already assigned to this tenant
            if (userTenantRoleRepository.findByUserAndTenant(user, tenant).isPresent()) {
                throw new DuplicateResourceException("Usuario ya asignado al tenant: " + tenant.getName());
            }

            // Validate account limit
            long currentCount = userTenantRoleRepository.countByTenant(tenant);
            if (currentCount >= tenant.getAccountLimit()) {
                throw new IllegalStateException("Tenant '" + tenant.getName() + "' ha alcanzado el limite de cuentas (" + tenant.getAccountLimit() + ")");
            }

            UserTenantRoleEntity userTenantRole = new UserTenantRoleEntity();
            userTenantRole.setUser(user);
            userTenantRole.setTenant(tenant);
            userTenantRole.setRole(assignment.getRole());

            userTenantRoles.add(userTenantRoleRepository.save(userTenantRole));
        }

        return mapToUserResponse(user, userTenantRoles);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> {
                    List<UserTenantRoleEntity> roles = userTenantRoleRepository.findByUserWithTenantAndApp(user);
                    return mapToUserResponse(user, roles);
                })
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        List<UserTenantRoleEntity> roles = userTenantRoleRepository.findByUserWithTenantAndApp(user);
        return mapToUserResponse(user, roles);
    }

    @Transactional
    public UserResponse assignTenant(Long userId, AssignTenantRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        TenantEntity tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        if (userTenantRoleRepository.findByUserAndTenant(user, tenant).isPresent()) {
            throw new DuplicateResourceException("Usuario ya asignado a este tenant");
        }

        // Validate account limit
        long currentCount = userTenantRoleRepository.countByTenant(tenant);
        if (currentCount >= tenant.getAccountLimit()) {
            throw new IllegalStateException("Tenant '" + tenant.getName() + "' ha alcanzado el limite de cuentas (" + tenant.getAccountLimit() + ")");
        }

        UserTenantRoleEntity userTenantRole = new UserTenantRoleEntity();
        userTenantRole.setUser(user);
        userTenantRole.setTenant(tenant);
        userTenantRole.setRole(request.getRole());

        userTenantRoleRepository.save(userTenantRole);

        List<UserTenantRoleEntity> roles = userTenantRoleRepository.findByUserWithTenantAndApp(user);
        return mapToUserResponse(user, roles);
    }

    @Transactional
    public void deleteUser(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        userRepository.delete(user);
    }

    /**
     * Elimina la relación de un usuario con un tenant específico.
     * NO elimina al usuario de la base de datos, solo su asociación con el tenant.
     *
     * @param userId ID del usuario
     * @param tenantId ID del tenant del cual se eliminará al usuario
     * @throws ResourceNotFoundException si el usuario o tenant no existen
     * @throws IllegalStateException si el usuario no pertenece a ese tenant
     */
    @Transactional
    public void removeUserFromTenant(Long userId, Long tenantId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        UserTenantRoleEntity userTenantRole = userTenantRoleRepository
                .findByUserAndTenant(user, tenant)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El usuario no pertenece a este tenant"));

        // Solo eliminar la relación, no el usuario
        userTenantRoleRepository.delete(userTenantRole);
    }

    @Transactional
    public UserResponse updateUserStatus(Long userId, boolean isActive) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        user.setIsActive(isActive);
        userRepository.save(user);

        List<UserTenantRoleEntity> roles = userTenantRoleRepository.findByUserWithTenantAndApp(user);
        return mapToUserResponse(user, roles);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByTenant(Long tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        List<UserTenantRoleEntity> userTenantRoles = userTenantRoleRepository.findByTenant(tenant);

        return userTenantRoles.stream()
                .map(utr -> {
                    List<UserTenantRoleEntity> allRoles = userTenantRoleRepository.findByUserWithTenantAndApp(utr.getUser());
                    return mapToUserResponse(utr.getUser(), allRoles);
                })
                .distinct()
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByTenantAdmin(UserEntity adminUser) {
        // Get all tenants where this user is TENANT_ADMIN
        List<UserTenantRoleEntity> adminTenants = userTenantRoleRepository.findByUserAndRole(adminUser, "TENANT_ADMIN");

        return adminTenants.stream()
                .flatMap(adminTenant -> {
                    List<UserTenantRoleEntity> tenantUsers = userTenantRoleRepository.findByTenant(adminTenant.getTenant());
                    return tenantUsers.stream()
                            .map(utr -> {
                                List<UserTenantRoleEntity> allRoles = userTenantRoleRepository.findByUserWithTenantAndApp(utr.getUser());
                                return mapToUserResponse(utr.getUser(), allRoles);
                            });
                })
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Obtiene usuarios que NO están asignados a ningún tenant.
     * Útil para que TENANT_ADMIN pueda buscar y asignar empleados.
     */
    public List<UserResponse> getUsersWithoutTenant() {
        List<UserEntity> allUsers = userRepository.findAll();

        return allUsers.stream()
                .filter(user -> {
                    List<UserTenantRoleEntity> roles = userTenantRoleRepository.findByUser(user);
                    return roles.isEmpty(); // Solo usuarios sin tenant asignado
                })
                .map(user -> mapToUserResponse(user, new java.util.ArrayList<>()))
                .collect(Collectors.toList());
    }

    private UserResponse mapToUserResponse(UserEntity user, List<UserTenantRoleEntity> userTenantRoles) {
        List<TenantRoleResponse> tenants = userTenantRoles.stream()
                .map(utr -> new TenantRoleResponse(
                        utr.getTenant().getId(),
                        utr.getTenant().getName(),
                        utr.getTenant().getApp().getName(),
                        utr.getRole()
                ))
                .collect(Collectors.toList());

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getIsActive(),
                user.getEmailVerified(),
                user.getProfilePictureUrl(),
                user.getAuthProvider(),
                tenants,
                user.getCreatedAt()
        );
    }

    /**
     * Actualiza la información de un usuario (email, fullName, password)
     */
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Actualizar campos si se proporcionan
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            // Verificar que el email no esté en uso por otro usuario
            if (!request.getEmail().equals(user.getEmail())) {
                if (userRepository.existsByEmail(request.getEmail())) {
                    throw new DuplicateResourceException("El email ya está en uso");
                }
                user.setEmail(request.getEmail());
            }
        }

        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            user.setFullName(request.getFullName());
        }

        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        user = userRepository.save(user);

        List<UserTenantRoleEntity> userTenantRoles = userTenantRoleRepository.findByUserWithTenantAndApp(user);
        return mapToUserResponse(user, userTenantRoles);
    }

    /**
     * Quita un tenant de un usuario
     */
    @Transactional
    public UserResponse removeTenantFromUser(Long userId, Long tenantId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        // Buscar la relación usuario-tenant
        UserTenantRoleEntity userTenantRole = userTenantRoleRepository
                .findByUserAndTenant(user, tenant)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario no está asignado a este tenant"));

        // Eliminar la relación
        userTenantRoleRepository.delete(userTenantRole);

        // Devolver usuario actualizado
        List<UserTenantRoleEntity> remainingRoles = userTenantRoleRepository.findByUserWithTenantAndApp(user);
        return mapToUserResponse(user, remainingRoles);
    }

    /**
     * Actualiza el rol de un usuario en un tenant específico
     */
    @Transactional
    public UserResponse updateUserTenantRole(Long userId, Long tenantId, UpdateTenantRoleRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        // Buscar la relación usuario-tenant
        UserTenantRoleEntity userTenantRole = userTenantRoleRepository
                .findByUserAndTenant(user, tenant)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario no está asignado a este tenant"));

        // Actualizar rol
        userTenantRole.setRole(request.getRole());
        userTenantRoleRepository.save(userTenantRole);

        // Devolver usuario actualizado
        List<UserTenantRoleEntity> userTenantRoles = userTenantRoleRepository.findByUserWithTenantAndApp(user);
        return mapToUserResponse(user, userTenantRoles);
    }
}