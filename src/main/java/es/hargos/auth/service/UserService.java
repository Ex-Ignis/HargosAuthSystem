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
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User with email " + request.getEmail() + " already exists");
        }

        TenantEntity tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        UserEntity user = new UserEntity();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setIsActive(true);
        user.setEmailVerified(false);

        user = userRepository.save(user);

        UserTenantRoleEntity userTenantRole = new UserTenantRoleEntity();
        userTenantRole.setUser(user);
        userTenantRole.setTenant(tenant);
        userTenantRole.setRole(request.getRole());

        userTenantRoleRepository.save(userTenantRole);

        return mapToUserResponse(user, List.of(userTenantRole));
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<UserTenantRoleEntity> roles = userTenantRoleRepository.findByUserWithTenantAndApp(user);
        return mapToUserResponse(user, roles);
    }

    @Transactional
    public UserResponse assignTenant(Long userId, AssignTenantRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        TenantEntity tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        if (userTenantRoleRepository.findByUserAndTenant(user, tenant).isPresent()) {
            throw new DuplicateResourceException("User already assigned to this tenant");
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        userRepository.delete(user);
    }

    @Transactional
    public UserResponse updateUserStatus(Long userId, boolean isActive) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setIsActive(isActive);
        userRepository.save(user);

        List<UserTenantRoleEntity> roles = userTenantRoleRepository.findByUserWithTenantAndApp(user);
        return mapToUserResponse(user, roles);
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
