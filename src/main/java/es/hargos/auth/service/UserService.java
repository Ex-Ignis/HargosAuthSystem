package es.hargos.auth.service;

import es.hargos.auth.dto.request.AssignTenantRequest;
import es.hargos.auth.dto.request.CreateUserRequest;
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

    @Transactional
    public UserResponse updateUserStatus(Long userId, boolean isActive) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        user.setIsActive(isActive);
        userRepository.save(user);

        List<UserTenantRoleEntity> roles = userTenantRoleRepository.findByUserWithTenantAndApp(user);
        return mapToUserResponse(user, roles);
    }

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
                tenants,
                user.getCreatedAt()
        );
    }
}
