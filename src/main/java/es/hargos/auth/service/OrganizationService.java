package es.hargos.auth.service;

import es.hargos.auth.dto.request.CreateOrganizationRequest;
import es.hargos.auth.dto.request.UpdateOrganizationRequest;
import es.hargos.auth.dto.response.OrganizationResponse;
import es.hargos.auth.entity.OrganizationEntity;
import es.hargos.auth.exception.DuplicateResourceException;
import es.hargos.auth.exception.ResourceNotFoundException;
import es.hargos.auth.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        if (organizationRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Organizacion con nombre: " + request.getName() + " ya existe");
        }

        OrganizationEntity organization = new OrganizationEntity();
        organization.setName(request.getName());
        organization.setDescription(request.getDescription());
        organization.setIsActive(true);

        organization = organizationRepository.save(organization);

        return mapToResponse(organization);
    }

    public List<OrganizationResponse> getAllOrganizations() {
        return organizationRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public OrganizationResponse getOrganizationById(Long id) {
        OrganizationEntity organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion no encontrada"));
        return mapToResponse(organization);
    }

    @Transactional
    public void deleteOrganization(Long id) {
        OrganizationEntity organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion no encontrada"));
        organizationRepository.delete(organization);
    }

    /**
     * Actualiza la información de una organización
     */
    @Transactional
    public OrganizationResponse updateOrganization(Long id, UpdateOrganizationRequest request) {
        OrganizationEntity organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organizacion no encontrada"));

        // Actualizar campos si se proporcionan
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            // Verificar que el nombre no esté en uso por otra organización
            if (!request.getName().equals(organization.getName())) {
                if (organizationRepository.existsByName(request.getName())) {
                    throw new DuplicateResourceException("El nombre ya está en uso");
                }
                organization.setName(request.getName());
            }
        }

        if (request.getDescription() != null) {
            organization.setDescription(request.getDescription());
        }

        organization = organizationRepository.save(organization);
        return mapToResponse(organization);
    }

    private OrganizationResponse mapToResponse(OrganizationEntity organization) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getDescription(),
                organization.getIsActive(),
                organization.getCreatedAt()
        );
    }
}
