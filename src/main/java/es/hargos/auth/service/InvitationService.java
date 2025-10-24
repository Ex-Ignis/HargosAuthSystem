package es.hargos.auth.service;

import es.hargos.auth.dto.request.CreateInvitationRequest;
import es.hargos.auth.dto.response.InvitationResponse;
import es.hargos.auth.entity.InvitationEntity;
import es.hargos.auth.entity.TenantEntity;
import es.hargos.auth.entity.UserEntity;
import es.hargos.auth.exception.DuplicateResourceException;
import es.hargos.auth.exception.ResourceNotFoundException;
import es.hargos.auth.repository.InvitationRepository;
import es.hargos.auth.repository.TenantRepository;
import es.hargos.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public InvitationResponse createInvitation(CreateInvitationRequest request, Long invitedByUserId) {
        // Verificar que el tenant existe
        TenantEntity tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        // Verificar que no exista una invitación pendiente para este email y tenant
        if (invitationRepository.existsByEmailAndTenantAndAccepted(request.getEmail(), tenant, false)) {
            throw new DuplicateResourceException(
                "Ya existe una invitación pendiente para " + request.getEmail() + " en este tenant"
            );
        }

        // Crear invitación
        InvitationEntity invitation = new InvitationEntity();
        invitation.setTenant(tenant);
        invitation.setEmail(request.getEmail());
        invitation.setToken(UUID.randomUUID().toString());
        invitation.setRole(request.getRole());
        invitation.setInvitedByUserId(invitedByUserId);
        invitation.setExpiresAt(LocalDateTime.now().plusDays(7)); // 7 días
        invitation.setAccepted(false);

        invitation = invitationRepository.save(invitation);

        // Enviar email
        try {
            emailService.sendInvitationEmail(request.getEmail(), tenant.getName(), invitation.getToken());
        } catch (Exception e) {
            // Log error but don't fail the invitation creation
            // El admin puede reenviar la invitación si falla el email
        }

        return mapToResponse(invitation);
    }

    public List<InvitationResponse> getInvitationsByTenant(Long tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        return invitationRepository.findByTenant(tenant).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<InvitationResponse> getPendingInvitationsByTenant(Long tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado"));

        return invitationRepository.findByTenantAndAccepted(tenant, false).stream()
                .filter(InvitationEntity::isValid) // Solo invitaciones válidas (no expiradas)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public InvitationEntity getInvitationByToken(String token) {
        return invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitación no encontrada o expirada"));
    }

    @Transactional
    public void markAsAccepted(InvitationEntity invitation) {
        invitation.setAccepted(true);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitationRepository.save(invitation);
    }

    @Transactional
    public void deleteInvitation(Long invitationId) {
        InvitationEntity invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitación no encontrada"));

        invitationRepository.delete(invitation);
    }

    /**
     * Obtiene el ID del tenant de una invitación
     * Útil para validar permisos antes de eliminarla
     */
    public Long getTenantIdByInvitationId(Long invitationId) {
        InvitationEntity invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitación no encontrada"));
        return invitation.getTenant().getId();
    }

    private InvitationResponse mapToResponse(InvitationEntity invitation) {
        InvitationResponse response = new InvitationResponse();
        response.setId(invitation.getId());
        response.setTenantId(invitation.getTenant().getId());
        response.setTenantName(invitation.getTenant().getName());
        response.setEmail(invitation.getEmail());
        response.setRole(invitation.getRole());
        response.setInvitedByUserId(invitation.getInvitedByUserId());

        if (invitation.getInvitedByUserId() != null) {
            userRepository.findById(invitation.getInvitedByUserId())
                    .ifPresent(user -> response.setInvitedByUserName(user.getFullName()));
        }

        response.setExpiresAt(invitation.getExpiresAt());
        response.setAccepted(invitation.getAccepted());
        response.setAcceptedAt(invitation.getAcceptedAt());
        response.setCreatedAt(invitation.getCreatedAt());
        response.setIsExpired(invitation.isExpired());
        response.setIsValid(invitation.isValid());

        return response;
    }
}
