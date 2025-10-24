package es.hargos.auth.service;

import es.hargos.auth.dto.request.CreateAccessCodeRequest;
import es.hargos.auth.dto.response.AccessCodeResponse;
import es.hargos.auth.entity.AccessCodeEntity;
import es.hargos.auth.entity.TenantEntity;
import es.hargos.auth.exception.ResourceNotFoundException;
import es.hargos.auth.repository.AccessCodeRepository;
import es.hargos.auth.repository.TenantRepository;
import es.hargos.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccessCodeService {

    private final AccessCodeRepository accessCodeRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    @Transactional
    public AccessCodeResponse createAccessCode(CreateAccessCodeRequest request, Long createdByUserId) {
        // Verificar que el tenant existe
        TenantEntity tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        // Generar código único
        String code = generateUniqueCode(tenant.getName());

        // Crear código de acceso
        AccessCodeEntity accessCode = new AccessCodeEntity();
        accessCode.setTenant(tenant);
        accessCode.setCode(code);
        accessCode.setRole(request.getRole());
        accessCode.setCreatedByUserId(createdByUserId);
        accessCode.setMaxUses(request.getMaxUses() != null ? request.getMaxUses() : 50); // 50 por defecto
        accessCode.setCurrentUses(0);

        if (request.getExpiresInDays() != null) {
            accessCode.setExpiresAt(LocalDateTime.now().plusDays(request.getExpiresInDays()));
        }

        accessCode.setIsActive(true);

        accessCode = accessCodeRepository.save(accessCode);

        return mapToResponse(accessCode);
    }

    public List<AccessCodeResponse> getAccessCodesByTenant(Long tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        return accessCodeRepository.findByTenant(tenant).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<AccessCodeResponse> getActiveAccessCodesByTenant(Long tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        return accessCodeRepository.findByTenantAndIsActive(tenant, true).stream()
                .filter(AccessCodeEntity::isValid) // Solo códigos válidos
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public AccessCodeEntity getAccessCodeByCode(String code) {
        return accessCodeRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Código de acceso no encontrado o expirado"));
    }

    @Transactional
    public void incrementUses(AccessCodeEntity accessCode) {
        accessCode.incrementUses();
        accessCodeRepository.save(accessCode);
    }

    @Transactional
    public void deactivateAccessCode(Long accessCodeId) {
        AccessCodeEntity accessCode = accessCodeRepository.findById(accessCodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Código de acceso no encontrado"));

        accessCode.setIsActive(false);
        accessCodeRepository.save(accessCode);
    }

    @Transactional
    public void deleteAccessCode(Long accessCodeId) {
        AccessCodeEntity accessCode = accessCodeRepository.findById(accessCodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Código de acceso no encontrado"));

        accessCodeRepository.delete(accessCode);
    }

    /**
     * Genera un código único en formato: TENANT-YEAR-XXXX
     * Ejemplo: ARENDEL-2025-X7K9
     */
    private String generateUniqueCode(String tenantName) {
        String sanitizedName = tenantName.toUpperCase()
                .replaceAll("[^A-Z0-9]", "")
                .substring(0, Math.min(10, tenantName.length()));

        int year = LocalDateTime.now().getYear();
        String randomPart = generateRandomString(4);

        String code = sanitizedName + "-" + year + "-" + randomPart;

        // Verificar que sea único (muy improbable colisión)
        while (accessCodeRepository.findByCode(code).isPresent()) {
            randomPart = generateRandomString(4);
            code = sanitizedName + "-" + year + "-" + randomPart;
        }

        return code;
    }

    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }

        return sb.toString();
    }

    private AccessCodeResponse mapToResponse(AccessCodeEntity accessCode) {
        AccessCodeResponse response = new AccessCodeResponse();
        response.setId(accessCode.getId());
        response.setTenantId(accessCode.getTenant().getId());
        response.setTenantName(accessCode.getTenant().getName());
        response.setCode(accessCode.getCode());
        response.setRole(accessCode.getRole());
        response.setCreatedByUserId(accessCode.getCreatedByUserId());

        if (accessCode.getCreatedByUserId() != null) {
            userRepository.findById(accessCode.getCreatedByUserId())
                    .ifPresent(user -> response.setCreatedByUserName(user.getFullName()));
        }

        response.setMaxUses(accessCode.getMaxUses());
        response.setCurrentUses(accessCode.getCurrentUses());

        if (accessCode.getMaxUses() != null) {
            response.setRemainingUses(accessCode.getMaxUses() - accessCode.getCurrentUses());
        }

        response.setExpiresAt(accessCode.getExpiresAt());
        response.setIsActive(accessCode.getIsActive());
        response.setCreatedAt(accessCode.getCreatedAt());
        response.setIsExpired(accessCode.isExpired());
        response.setHasReachedMaxUses(accessCode.hasReachedMaxUses());
        response.setIsValid(accessCode.isValid());

        return response;
    }
}
