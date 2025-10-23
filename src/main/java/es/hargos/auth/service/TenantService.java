package es.hargos.auth.service;

import es.hargos.auth.dto.request.CreateTenantRequest;
import es.hargos.auth.dto.response.TenantResponse;
import es.hargos.auth.entity.AppEntity;
import es.hargos.auth.entity.OrganizationEntity;
import es.hargos.auth.entity.TenantEntity;
import es.hargos.auth.exception.DuplicateResourceException;
import es.hargos.auth.exception.ResourceNotFoundException;
import es.hargos.auth.repository.AppRepository;
import es.hargos.auth.repository.OrganizationRepository;
import es.hargos.auth.repository.TenantRepository;
import es.hargos.auth.repository.UserTenantRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final AppRepository appRepository;
    private final OrganizationRepository organizationRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        AppEntity app = appRepository.findById(request.getAppId())
                .orElseThrow(() -> new ResourceNotFoundException("App no encontrada"));

        OrganizationEntity organization = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion no encontrada"));

        if (tenantRepository.existsByAppAndName(app, request.getName())) {
            throw new DuplicateResourceException("Tenant con nombre: " + request.getName() + " ya existe para esta app");
        }

        TenantEntity tenant = new TenantEntity();
        tenant.setApp(app);
        tenant.setOrganization(organization);
        tenant.setName(request.getName());
        tenant.setDescription(request.getDescription());
        tenant.setAccountLimit(request.getAccountLimit());
        tenant.setRiderLimit(request.getRiderLimit());
        tenant.setIsActive(true);

        tenant = tenantRepository.save(tenant);

        return mapToResponse(tenant);
    }

    public List<TenantResponse> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TenantResponse getTenantById(Long id) {
        TenantEntity tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));
        return mapToResponse(tenant);
    }

    public List<TenantResponse> getTenantsByOrganization(Long organizationId) {
        OrganizationEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion no encontrada"));

        return tenantRepository.findByOrganization(organization).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteTenant(Long id) {
        TenantEntity tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));
        tenantRepository.delete(tenant);
    }

    private TenantResponse mapToResponse(TenantEntity tenant) {
        long currentAccountCount = userTenantRoleRepository.countByTenant(tenant);

        return new TenantResponse(
                tenant.getId(),
                tenant.getApp().getId(),
                tenant.getApp().getName(),
                tenant.getOrganization().getId(),
                tenant.getOrganization().getName(),
                tenant.getName(),
                tenant.getDescription(),
                tenant.getAccountLimit(),
                currentAccountCount,
                tenant.getRiderLimit(),
                null, // currentRiderCount - lo enviará el droplet
                tenant.getIsActive(),
                tenant.getCreatedAt()
        );
    }
}
